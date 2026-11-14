package com.fssc.framework.org;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import org.springframework.stereotype.Component;

/**
 * 组织合法性中央守卫（按"数据权限-组织维度"校验，三路体检后统一办结）。
 *
 * <p>体检结论：tenant_id 由拦截器强制隔离可靠；但租户内分子公司(company_id)在
 * 读路径（page/detail/报表全租户可见+IDOR）、写路径（companyId 前端传值即采信）、
 * 操作路径（complete 零操作人校验）三条边均无防线。本组件提供统一守卫：</p>
 * <ul>
 *   <li>{@link #assertCompanyAllowed} —— 写入守卫：payload 公司必须等于登录人公司；</li>
 *   <li>{@link #enforceCompany()} —— 读取注入：返回当前用户强制公司范围（null=集团级不限制）；</li>
 *   <li>{@link #assertVisible} —— 详情可见性：本公司 或 本人单据 或 集团级账号。</li>
 * </ul>
 * 兼容策略（演示/集团岗零破坏）：company_id 为 NULL 的集团级账号一律放行；
 * 单据公司为空（主数据/无公司维度域）放行（fail-open，不卡无组织维度流程）。
 */
@Component
public class OrgGuard {

    /** 当前登录用户所属公司；未登录或集团级账号返回 null */
    public Long currentCompany() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getCompanyId();
    }

    /** 读取侧强制公司范围：集团级账号返回 null（不限制），普通用户返回其公司ID */
    public Long enforceCompany() {
        return currentCompany();
    }

    /**
     * 写入守卫：payload 携带的 companyId 必须与登录人公司一致。
     * 集团级账号（无公司）或 payload 未指定公司时放行（后者由写入路径兜底赋值）。
     */
    public void assertCompanyAllowed(Long companyId) {
        Long mine = currentCompany();
        if (mine == null || companyId == null) {
            return;
        }
        if (!mine.equals(companyId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "组织合法性校验不通过：无权以该公司主体建单/操作（单据公司 " + companyId + " ≠ 当前用户公司 " + mine
                            + "）。规则：单据公司必须等于建单人所属公司；请改用目标公司 " + companyId
                            + " 下的提单人建单，或先切换到该公司（X-Company-Id）后再操作。");
        }
    }

    /**
     * 详情可见性守卫：本公司单据、本人提交的单据、集团级账号、集团数据范围账号可见；否则拒绝。
     *
     * <p>合规修复（H-01，公司31 DRAFT 凭证无法过账）：共享中心集团岗（如 GROUP_GL 集团总账会计）
     * 所属公司为公司1，但需跨公司（如公司31）执行过账等集团核算职责。其角色
     * data_scope=1（全部数据权限，AuthService 登录时多角色取最宽 min 聚合进 Token），
     * 本守卫对 data_scope=1 的用户豁免公司维度可见性限制；其余数据范围不受影响。</p>
     *
     * @param billCompany 单据所属公司（可为 null=无公司维度，放行）
     * @param applyUserId 单据提交人（可为 null）
     */
    public void assertVisible(Long billCompany, Long applyUserId) {
        Long mine = currentCompany();
        if (mine == null || billCompany == null || billCompany.equals(mine)) {
            return;
        }
        LoginUser u = UserContext.get();
        // 集团数据范围豁免：data_scope=1（全部数据权限）放行跨公司查看/操作
        if (u != null && u.getDataScope() != null && u.getDataScope() == 1) {
            return;
        }
        if (u != null && applyUserId != null && applyUserId.equals(u.getUserId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权查看他公司单据（单据公司 " + billCompany + "）");
    }
}
