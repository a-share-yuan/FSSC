package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.annotation.ReportDomain;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 报表业务域防线（报表权限整改）：
 * 校验当前用户持有 {@code @ReportDomain} 声明域的任一判据权限码，
 * 即「业务域无业务权限 → 该域报表接口必拒」的运行期强制。
 *
 * <p>放行条件（任一）：
 * <ol>
 *   <li>超管通配码 {@code *:*:*}；</li>
 *   <li>审计阅示码 {@code report:audit:all}（内审/董事会等监督岗白名单，季度复核）；</li>
 *   <li>持有该域任一判据权限码。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
public class ReportDomainAspect {

    @Around("@annotation(reportDomain)")
    public Object around(ProceedingJoinPoint pjp, ReportDomain reportDomain) throws Throwable {
        LoginUser u = UserContext.getRequired();
        var perms = u.getPerms();
        boolean wildcard = perms != null && perms.contains("*:*:*");
        // 注意：本类中简单名 ReportDomain 是注解；枚举用全限定名引用
        boolean auditAll = perms != null && perms.contains(com.fssc.framework.security.ReportDomain.AUDIT_ALL);
        if (!wildcard && !auditAll) {
            var domain = com.fssc.framework.security.ReportDomain.of(reportDomain.value());
            // hitForRead = 业务判据码 ∪ 域报表查看码(report:<domain>view，管理层阅示凭证)
            if (!domain.hitForRead(perms)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED,
                        "缺少「" + domain.getLabel() + "」业务权限，无权访问该域报表（需任一："
                                + String.join(" / ", domain.getJudgeCodes()) + "）");
            }
        }
        return pjp.proceed();
    }
}
