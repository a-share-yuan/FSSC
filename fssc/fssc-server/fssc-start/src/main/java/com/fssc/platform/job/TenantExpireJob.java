package com.fssc.platform.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.platform.entity.PfTenant;
import com.fssc.platform.mapper.PfTenantMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 租户到期冻结作业。
 *
 * <p>改造前只有「懒冻结」：{@code PfTenantStatusValidator} 在请求入口实时拦过期租户（该拦继续保留），
 * 但没有任何作业把 {@code pf_tenant.status} 从 NORMAL 改成 FROZEN——平台列表读到的仍是"正常"，
 * 运营看到的与用户实际体验到的不是一件事。到期 3 个月没续费的租户在后台也显示绿色。
 *
 * <p>本作业每天 01:20 跑：把 {@code status='NORMAL' 且 expire_time < now()} 的租户置 FROZEN。
 * 入口实时拦保持不变（双保险：作业没跑到的窗口里，用户依然访问不了）。
 *
 * <p>pf_* 是平台表（租户拦截器白名单内），作业线程无租户上下文也能直接读写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireJob {

    private final PfTenantMapper pfTenantMapper;
    private final JobHeartbeatService jobHeartbeatService;

    @Scheduled(cron = "0 20 1 * * ?")
    public void freezeExpired() {
        jobHeartbeatService.run("TenantExpireJob", () -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                // pf_* 属平台表（租户拦截器白名单），作业线程无租户上下文可直接读写，不需要 callAs
                List<PfTenant> expired = pfTenantMapper.selectList(
                        new LambdaQueryWrapper<PfTenant>()
                                .eq(PfTenant::getStatus, "NORMAL")
                                .isNotNull(PfTenant::getExpireTime)
                                .lt(PfTenant::getExpireTime, now)
                                .eq(PfTenant::getDelFlag, 0));
                if (expired == null || expired.isEmpty()) {
                    return;
                }
                for (PfTenant t : expired) {
                    pfTenantMapper.update(null, new LambdaUpdateWrapper<PfTenant>()
                            .set(PfTenant::getStatus, "FROZEN")
                            .eq(PfTenant::getId, t.getId())
                            .eq(PfTenant::getStatus, "NORMAL"));
                    log.info("租户 {}（{}）已于 {} 到期，状态 NORMAL → FROZEN",
                            t.getTenantCode(), t.getTenantName(), t.getExpireTime());
                }
            } catch (Exception ex) {
                log.error("[租户到期冻结] 执行失败", ex);
                throw ex;
            }
        });
    }
}
