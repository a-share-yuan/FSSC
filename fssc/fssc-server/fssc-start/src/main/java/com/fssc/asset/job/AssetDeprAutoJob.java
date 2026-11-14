package com.fssc.asset.job;

import com.fssc.asset.entity.AssetDeprBatch;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.mapper.MdAssetMapper;
import com.fssc.asset.service.AssetDeprBatchService;
import com.fssc.framework.job.JobHeartbeatService;
import com.fssc.framework.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资产月度自动计提 Job。
 *
 * <p>每月 1 日 02:00 自动触发折旧计提批单生成 + 发起 FA_DEPRECIATION 审批流。
 * 省掉人工调接口触发，接通"投资到资产"循环的自动化最后一环。
 *
 * <p>多租户处理：扫描有在用资产的租户，逐租户发起计提。
 * 去重由 {@link AssetDeprBatchService#createAndSubmit} 的批单状态 + 资产 lastDeprDate 保障。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetDeprAutoJob {

    private final AssetDeprBatchService deprBatchService;
    private final MdAssetMapper assetMapper;
    private final JobHeartbeatService jobHeartbeatService;

    /** 每月 1 日 02:00 执行（避开 01:xx 的其他日终批处理高峰） */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void autoDepreciate() {
        jobHeartbeatService.run("AssetDeprAutoJob", () -> {
            try {
                // 扫描所有有在用资产的租户
                List<MdAsset>[] holder = new List[1];
                TenantContext.runIgnoreTenant(() ->
                        holder[0] = assetMapper.selectList(new LambdaQueryWrapper<MdAsset>()
                                .ne(MdAsset::getStatus, "SCRAP")));
                List<MdAsset> active = holder[0];
                if (active == null || active.isEmpty()) {
                    log.info("[资产自动计提] 无在用资产，跳过");
                    return;
                }
                Set<Long> tenantIds = active.stream()
                        .map(MdAsset::getTenantId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                log.info("[资产自动计提] 扫描到 {} 条在用资产，涉及 {} 个租户", active.size(), tenantIds.size());

                String period = YearMonth.now().toString();
                for (Long tenantId : tenantIds) {
                    try {
                        TenantContext.callAs(tenantId, () -> {
                            AssetDeprBatch batch = deprBatchService.createAndSubmit(period);
                            log.info("[资产自动计提] 租户 {} 已发起折旧批单 id={} period={}",
                                    tenantId, batch.getId(), period);
                        });
                    } catch (Exception ex) {
                        log.error("[资产自动计提] 租户 {} 计提发起失败", tenantId, ex);
                    }
                }
            } catch (Exception ex) {
                log.error("[资产自动计提] 扫描异常", ex);
            }
        });
    }
}
