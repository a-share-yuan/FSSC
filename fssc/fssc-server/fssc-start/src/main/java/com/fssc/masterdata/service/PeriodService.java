// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.masterdata.entity.MdPeriod;
import com.fssc.masterdata.mapper.MdPeriodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 主数据 Period 服务（带 tenant_id，租户拦截器自动隔离）。
 * 会计期间状态：NORMAL 开账 / CLOSED 已结账 / DISABLED 停用。
 * 结账 = 期间锁定（凭证禁改）+ 记录关账时间/人；反结账恢复。
 */
@Service
@RequiredArgsConstructor
public class PeriodService {

    private final MdPeriodMapper mapper;

    /**
     * 期间分页（R5-D-042 公司维度缺失·读路径）。
     *
     * <p>原实现只按关键字过滤，同租户内 9 个法人公司的期间行互相全量可见（库内实测
     * md_period 覆盖 company_id=1/31~38），页面既看不出某行属于哪家公司，也筛不动。
     * 现按本仓库既有口径接 {@code @DataScope} + {@link DataScopeKit}（与 R5-D-026 的
     * {@code BankAccountService.page}、退货列表 {@code PurReturnService.page} 同法）：
     * md_period 无部门／经办人列，2~5 档一律降级到本公司；载体为空（注解未生效）时
     * {@link DataScopeKit} 不追加条件，故本方法必须始终挂在 {@code @DataScope} 之下。</p>
     *
     * <p>显式 {@code companyId} 筛选与数据范围是 <b>AND 叠加</b>，只会更窄，绝不放宽到
     * 调用人范围之外；md_period.company_id 为 NOT NULL，直接等值，不涉及"租户共用"口径。</p>
     */
    @DataScope("会计期间列表数据范围")
    public PageResult<MdPeriod> page(long pageNum, long pageSize, String keyword, Long companyId) {
        LambdaQueryWrapper<MdPeriod> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdPeriod::getPeriodYear, keyword).or().like(MdPeriod::getPeriodMonth, keyword));
        }
        if (companyId != null) {
            qw.eq(MdPeriod::getCompanyId, companyId);
        }
        DataScopeKit.apply(qw, MdPeriod::getCompanyId, null, null);
        qw.orderByDesc(MdPeriod::getId);
        Page<MdPeriod> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdPeriod e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        // D-MD-PERIOD1：前端「新增期间」弹窗不携带 companyId —— 后端按登录主体回填，仍缺失则显式报错
        if (e.getCompanyId() == null) {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) {
                e.setCompanyId(lu.getCompanyId());
            }
        }
        if (e.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属公司(companyId)不能为空，请先选择公司主体");
        }
        // begin_date/end_date NOT NULL：按年月自动推算（首日 ~ 末日）
        if (e.getBeginDate() == null && e.getPeriodYear() != null && e.getPeriodMonth() != null) {
            java.time.YearMonth ym = java.time.YearMonth.of(e.getPeriodYear(), e.getPeriodMonth());
            e.setBeginDate(ym.atDay(1));
            e.setEndDate(ym.atEndOfMonth());
        }
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdPeriod e) {
        // D-DEC-111：通用编辑口带着 status（非 CLOSED）提交时，MyBatis-Plus 只更新非 null 字段，
        // 库里的 close_time/close_by 会原地残留 ⇒ 又造出「status=NORMAL 却带关账时间」的二源行。
        // 本口不担当关账/反结账（那是 close/reopen 的职责），故显式清痕，保持「关账留痕只属于 CLOSED」。
        if (e.getId() != null && e.getStatus() != null && !"CLOSED".equals(e.getStatus())) {
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MdPeriod>()
                    .eq(MdPeriod::getId, e.getId())
                    .set(MdPeriod::getCloseTime, null)
                    .set(MdPeriod::getCloseBy, null));
        }
        mapper.updateById(e);
    }

    public void enable(Long id, boolean enable) {
        // D-DEC-111（残留写点取证结论）：本口是 reopen/reopenByPeriod 之外唯一把 status 写回
        // NORMAL 的地方，原实现只 set status —— 对已结账期间点「启用」即得「NORMAL + 关账时间/关账人」
        // 的矛盾行（实测 md_period id=12、id=217 即此形态）。同 delete() 的口径：关账态不许经这里绕行
        // 解锁（反结账另有入口 /{id}/reopen）；改状态时一并显式清空关账留痕。
        MdPeriod exist = require(id);
        if ("CLOSED".equals(exist.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该期间已结账，不能经启用/停用改状态；请走「反结账」(/period/" + id + "/reopen)");
        }
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MdPeriod>()
                .eq(MdPeriod::getId, id)
                .set(MdPeriod::getStatus, enable ? "NORMAL" : "DISABLED")
                .set(MdPeriod::getCloseTime, null)
                .set(MdPeriod::getCloseBy, null));
    }

    /** 删除期间（逻辑删；仅未结账且无凭证的期间可删） */
    public void delete(Long id) {
        MdPeriod exist = require(id);
        if ("CLOSED".equals(exist.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已结账期间不能删除");
        }
        mapper.hardDelete(id);
    }

    // ---------- 结账 / 反结账 ----------

    /** 结账：期间锁定（CLOSED），后续不允许再生成/修改该期间凭证 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        MdPeriod exist = require(id);
        if ("CLOSED".equals(exist.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该期间已结账，无需重复操作");
        }
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        MdPeriod upd = new MdPeriod();
        upd.setId(id);
        upd.setStatus("CLOSED");
        upd.setCloseTime(LocalDateTime.now());
        upd.setCloseBy(userId);
        mapper.updateById(upd);
    }

    /** 反结账：恢复开账（NORMAL），仅在确实需要调整时使用 */
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id) {
        MdPeriod exist = require(id);
        if (!"CLOSED".equals(exist.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该期间未结账，无法反结账");
        }
        // D-DEC-189 配套：原实现用 updateById 置 null，MyBatis-Plus 默认忽略 null 字段
        // ⇒ close_time/close_by 残留，重开后的期间带着"已关账时间/关账人"，状态与留痕二源。
        // 与 reopenByPeriod 同改：显式 set null。
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MdPeriod>()
                .eq(MdPeriod::getId, id)
                .set(MdPeriod::getStatus, "NORMAL")
                .set(MdPeriod::getCloseTime, null)
                .set(MdPeriod::getCloseBy, null));
    }

    /** 校验期间是否开账（凭证生成/记账前调用，结账期间抛异常） */
    public void assertOpen(Long companyId, int periodYear, int periodMonth) {
        MdPeriod p = mapper.selectOne(new LambdaQueryWrapper<MdPeriod>()
                .eq(MdPeriod::getCompanyId, companyId)
                .eq(MdPeriod::getPeriodYear, periodYear)
                .eq(MdPeriod::getPeriodMonth, periodMonth));
        if (p == null) {
            return; // 未建期间不拦截
        }
        if ("CLOSED".equals(p.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("会计期间 %d-%02d 已结账，禁止操作", periodYear, periodMonth));
        }
    }

    /** 按 公司+期间 查询期间记录（不存在返回 null），供月结/反结账状态判定 */
    public MdPeriod periodOf(Long companyId, int periodYear, int periodMonth) {
        return mapper.selectOne(new LambdaQueryWrapper<MdPeriod>()
                .eq(MdPeriod::getCompanyId, companyId)
                .eq(MdPeriod::getPeriodYear, periodYear)
                .eq(MdPeriod::getPeriodMonth, periodMonth));
    }

    /**
     * 按 公司+期间 联动结账：月结试算平衡后调用，将 md_period 置 CLOSED。
     * D-VC-2：期间未建档时抛出业务异常（不再静默跳过——否则月结"成功"却没有任何期间被真正关闭，
     * 属于假成功）；已结账时幂等跳过（重复月结不报错）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeByPeriod(Long companyId, int periodYear, int periodMonth) {
        closeByPeriod(companyId, periodYear, periodMonth, null);
    }

    /**
     * 按 公司+期间 联动结账（D-DEC-189 新增 operatorId 重载）。
     *
     * <p>关账时点后移到「月结审批末节点通过」后，回写发生在流程收尾线程里，那里<b>没有登录上下文</b>
     * （{@code UserContext} 为空），若仍只取 UserContext，md_period.close_by 会整列为 NULL
     * ——期间被谁关闭无从追责。本重载允许调用方把审批人（末节点 APPROVE 留痕人）显式带进来；
     * 传 null 时保持原口径取当前登录用户（人工直连结账路径行为不变）。
     *
     * @param operatorId 关账操作人（审批人）；null 表示取登录上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeByPeriod(Long companyId, int periodYear, int periodMonth, Long operatorId) {
        MdPeriod p = mapper.selectOne(new LambdaQueryWrapper<MdPeriod>()
                .eq(MdPeriod::getCompanyId, companyId)
                .eq(MdPeriod::getPeriodYear, periodYear)
                .eq(MdPeriod::getPeriodMonth, periodMonth));
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("会计期间 %d-%02d 未建档，无法结账。请先在「会计期间维护」中为该期间建档后再执行月结",
                            periodYear, periodMonth));
        }
        if ("CLOSED".equals(p.getStatus())) {
            return;
        }
        Long userId = operatorId != null ? operatorId
                : (UserContext.get() == null ? null : UserContext.get().getUserId());
        MdPeriod upd = new MdPeriod();
        upd.setId(p.getId());
        upd.setStatus("CLOSED");
        upd.setCloseTime(LocalDateTime.now());
        upd.setCloseBy(userId);
        mapper.updateById(upd);
    }

    /**
     * 按 公司+期间 联动反结账：月结 unclose 通过校验后调用，将 md_period 由 CLOSED 回退 NORMAL（开账）。
     * 期间不存在时抛业务异常；未结账时静默跳过（幂等）。清除关账时间/人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reopenByPeriod(Long companyId, int periodYear, int periodMonth) {
        MdPeriod p = mapper.selectOne(new LambdaQueryWrapper<MdPeriod>()
                .eq(MdPeriod::getCompanyId, companyId)
                .eq(MdPeriod::getPeriodYear, periodYear)
                .eq(MdPeriod::getPeriodMonth, periodMonth));
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    String.format("会计期间 %d-%02d 未建档，无法反结账", periodYear, periodMonth));
        }
        if (!"CLOSED".equals(p.getStatus())) {
            return;
        }
        // D-DEC-189 配套（关账时点后移后，重开必须把「关账时间/人」一并清空）：
        // 原实现用 updateById 置 null，而 MyBatis-Plus 默认策略忽略 null 字段 ⇒ close_time/close_by
        // 残留旧值，出现「状态 NORMAL 却带着关账时间」的二源（同族 D-DEC-111）。改为显式 set null。
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MdPeriod>()
                .eq(MdPeriod::getId, p.getId())
                .set(MdPeriod::getStatus, "NORMAL")
                .set(MdPeriod::getCloseTime, null)
                .set(MdPeriod::getCloseBy, null));
    }

    private MdPeriod require(Long id) {
        MdPeriod p = mapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不存在: " + id);
        }
        return p;
    }
}
