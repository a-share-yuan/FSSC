package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlSharedMaster;
import com.fssc.glex.mapper.GlSharedMasterMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * GL 主数据维护服务（gl_shared_master）。科目/成本中心/银行等主数据变更申请，
 * 提交即发起 GL_SHARED_MASTER 审批流（总账会计申请→财务经理→共享主管）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlSharedMasterService {

    private final GlSharedMasterMapper mapper;
    private final WfEngineFacade wfEngineFacade;

    public PageResult<GlSharedMaster> page(long pageNum, long pageSize, String period, String type, String keyword) {
        LambdaQueryWrapper<GlSharedMaster> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlSharedMaster::getPeriod, period);
        }
        if (StringUtils.hasText(type)) {
            qw.eq(GlSharedMaster::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlSharedMaster::getReqNo, keyword).or().like(GlSharedMaster::getSubjectCode, keyword)
                    .or().like(GlSharedMaster::getSubjectName, keyword));
        }
        qw.orderByDesc(GlSharedMaster::getId);
        Page<GlSharedMaster> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 提交 GL 主数据维护申请并置 APPROVED（生成凭证号），随后发起 GL_SHARED_MASTER 审批流。
     */
    @Transactional(rollbackFor = Exception.class)
    public GlSharedMaster submit(String period, String type, String action,
                                 String subjectCode, String subjectName, String description) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        if (!period.matches("\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间格式应为 yyyyMM，如 202609");
        }
        if (!StringUtils.hasText(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主数据类型不能为空");
        }
        if (!StringUtils.hasText(action)) {
            action = "ADD";
        }
        // 主数据编码是人工输入的外部编码（非系统自动编号），缺了后续凭证/科目挂不上；停用只认编码，名称可空。
        if (!StringUtils.hasText(subjectCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主数据编码不能为空");
        }
        if (!"DELETE".equals(action) && !StringUtils.hasText(subjectName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主数据名称不能为空");
        }
        Long tenantId = TenantContext.getTenantId();
        long seq = countByPeriod(period) + 1;
        GlSharedMaster e = new GlSharedMaster();
        e.setTenantId(tenantId);
        e.setPeriod(period);
        e.setType(type);
        e.setAction(action);
        e.setSubjectCode(subjectCode);
        e.setSubjectName(subjectName);
        e.setDescription(StringUtils.hasText(description) ? description
                : "GL" + typeName(type) + "主数据" + actionName(action));
        e.setReqNo("MS" + period + String.format("%03d", seq));
        // 不预造 GLMS 凭证号——本申请不产生凭证，凭证号只能由真实凭证回写；无凭证则留空
        e.setStatus("APPROVED");
        mapper.insert(e);
        // G1：主数据维护发起 GL_SHARED_MASTER 审批流
        try {
            wfEngineFacade.start("GL_SHARED_MASTER", e.getId(), userId(),
                    typeName(type) + "主数据" + actionName(action) + "-" + period, new BigDecimal("0"));
        } catch (Exception ex) {
            log.error("GL 主数据维护 {} 发起审批失败: {}", period, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
        }
        return e;
    }

    private String typeName(String type) {
        return switch (type) {
            case "SUBJECT" -> "科目";
            case "COST_CENTER" -> "成本中心";
            case "BANK" -> "银行";
            default -> "其他";
        };
    }

    private String actionName(String action) {
        return switch (action) {
            case "ADD" -> "新增";
            case "MODIFY" -> "变更";
            case "DELETE" -> "停用";
            default -> action;
        };
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlSharedMaster>().eq(GlSharedMaster::getPeriod, period));
    }
}