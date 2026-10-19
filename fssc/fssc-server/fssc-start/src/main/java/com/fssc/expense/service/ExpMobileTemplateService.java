package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.expense.entity.ExpMobileTemplate;
import com.fssc.expense.mapper.ExpMobileTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 移动端单据启用清单（exp_mobile_template）只读服务——本表的唯一读入口，本期无写入口。
 *
 * <p><b>它解决什么</b>：H5 提报页的可选单据类型此前由前端常量 {@code H5_DOC_TYPES} 硬编码，
 * 清单调整必须重新构建并发版前端。本类把清单改成库里读，前端拿到什么就渲染什么。</p>
 *
 * <p><b>为什么返回全量（含 {@code mobileEnabled=0} 的行）而不是只返回启用的</b>：
 * 页面上「以下类型请在电脑端提报」那块明示区用的正是这些 0 值行。只下发 1 值行会让不可提的类型
 * 从入口里静默消失——用户无法区分"这个业务不存在"和"这个业务存在但要去电脑端办"，
 * 前者会让人重复提单或干脆放弃。故排序口径是 {@code mobileEnabled} 降序 + {@code sort} 升序：
 * 可提的排前面，明示区保持 seed 原序。</p>
 *
 * <p><b>两道条件不在本类手写</b>（与本仓既有做法一致）：
 * <ul>
 *  <li>{@code del_flag = 0} —— 基类 {@code TenantAuditBase#delFlag} 的 {@code @TableLogic} 由 MP 自动追加；</li>
 *  <li>{@code tenant_id} —— {@code TenantLineInnerInterceptor} 自动注入（{@code MybatisPlusConfig}，
 *      租户拦截器先于分页注册）。本表不在 {@code FsscTenantLineHandler} 的全局表白名单内，
 *      属"应隔离"的默认侧，<b>不得</b>为其加白名单。</li>
 * </ul>
 *
 * <p><b>读失败不抛给页面（取舍，写清楚）</b>：表未建、SQL 异常、租户上下文缺失等一律 catch 后
 * 返回空清单。理由是清单是<b>展示面</b>而非<b>校验面</b>——它不决定一张单能不能提交、
 * 也不参与任何金额或审批判断，把"配置读不到"升级成"提报页整片打不开"是 disproportionate 的失败。
 * 代价如实记两条：①空清单在页面上与"库里真的一行没有"不可区分，前端必须保留常量兜底，
 * 不能把空数组渲染成"移动端暂不支持任何类型"；②降级只在日志里留痕（warn），界面无提示。
 * 若将来本表被任何写侧/校验侧（例如提交时拦越权类型）消费，<b>该 try-catch 必须一并重评</b>：
 * 那时读失败返回空＝闸门全开，是不能接受的。</p>
 *
 * <p>只读：本类不含任何 INSERT/UPDATE/DELETE，不记操作日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpMobileTemplateService {

    private final ExpMobileTemplateMapper expMobileTemplateMapper;

    /**
     * 移动端单据启用清单（全量，含未启用行）。
     * 排序＝{@code mobile_enabled} 降序 + {@code sort} 升序 + {@code id} 升序（末位兜底：
     * {@code sort} 允许运营填同值，不加 id 则同值行的相对次序不稳定，前端列表会闪跳）。
     * 任何异常返回空列表，不向上抛。
     */
    public List<ExpMobileTemplate> listForMobile() {
        try {
            List<ExpMobileTemplate> rows = expMobileTemplateMapper.selectList(
                    new LambdaQueryWrapper<ExpMobileTemplate>()
                            .orderByDesc(ExpMobileTemplate::getMobileEnabled)
                            .orderByAsc(ExpMobileTemplate::getSort)
                            .orderByAsc(ExpMobileTemplate::getId));
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            // 表不存在（脚本尚未执行 / 已回滚）或租户上下文缺失都走这里：按"清单为空"降级，
            // 前端回落常量渲染，提报页不因配置面读不到而打不开。理由与代价见类注释。
            log.warn("移动端单据启用清单读取失败（按空清单降级，前端回落常量）: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
