package com.fssc.document.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.document.entity.OpArchiveItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 案卷组卷编排（一卷多件）。
 *
 * <p><b>为什么单开一个类</b>：归档整理的层级是「全宗 → 年度/机构/保管期限 → 案卷 → 件 → 页」，
 * "件"才是最小整理单位（DA/T 22—2015《归档文件整理规则》），而本系统原先把"卷"退化成了"件"——
 * {@code op_archive} 一次只挂一张单据、判重键又按来源单据，于是没有卷内目录、没有件号。
 * 本类只做<b>编排</b>：把"建卷＋按序加件"这条动作串起来给控制器用；件行怎么写、跨公司怎么拦、
 * 可改窗口怎么判，全部收在 {@link ArchiveService} 一处（{@code writeVolumeItem}/{@code addVolumeItem}/
 * {@code requireEditableVolume}），不要两处各写一套件规则——的成因正是"管控"与"写入"各写一版。
 *
 * <p>卷的编号、必填校验、期间回落、原件保管单位与首件镜像沿用既有 {@code createArchive}，
 * 该方法已同步落首件行（件号 1），所以本类只补第 2 件起的单据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveVolumeService {

    private final ArchiveService archiveService;

    /** 卷内目录（件号正序，含文件数）。 */
    public List<OpArchiveItem> listItems(Long archiveId) {
        return archiveService.listVolumeItems(archiveId);
    }

    /**
     * 新建案卷并一次挂多件。
     *
     * @param billIds 来源单据主键，顺序即件号顺序；至少一枚
     * @param carrierType PAPER/ELECTRONIC（ARC-M4）；空由 {@code ArchiveService.createArchive} 归一为 PAPER
     * @return 新卷主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createVolume(String archiveType, Integer archiveYear, String archivePeriod,
                             Integer retentionPeriod, List<Long> billIds, String carrierType) {
        List<Long> ids = distinct(billIds);
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少选择一张待归档单据");
        }
        Long archiveId = archiveService.createArchive(ids.get(0), archiveType, archiveYear, archivePeriod,
                retentionPeriod, carrierType);
        for (int i = 1; i < ids.size(); i++) {
            archiveService.addVolumeItem(archiveId, ids.get(i));
        }
        log.info("新建案卷 id={} 共 {} 件，来源单据 {}", archiveId, ids.size(), ids);
        return archiveId;
    }

    /** 向待归档卷追加多件；全部已在卷内时如实报错（不要静默"成功 0 件"）。 */
    @Transactional(rollbackFor = Exception.class)
    public int addItems(Long archiveId, List<Long> billIds) {
        List<Long> ids = distinct(billIds);
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要加入本卷的单据");
        }
        int added = 0;
        for (Long billId : ids) {
            OpArchiveItem before = null;
            for (OpArchiveItem i : archiveService.listVolumeItems(archiveId)) {
                if (billId.equals(i.getBillId())) {
                    before = i;
                    break;
                }
            }
            if (before != null) {
                continue;
            }
            archiveService.addVolumeItem(archiveId, billId);
            added++;
        }
        if (added == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所选单据均已在本卷内，未重复加入");
        }
        return added;
    }

    /** 把一件移出本卷（只解绑，不要动单据、不要动卷）。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long archiveId, Long itemId) {
        archiveService.removeVolumeItem(archiveId, itemId);
    }

    private List<Long> distinct(List<Long> billIds) {
        List<Long> out = new ArrayList<>();
        if (billIds == null) {
            return out;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : billIds) {
            if (id != null && id > 0L && seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
