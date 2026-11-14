package com.fssc.framework.facade;

import com.fssc.common.core.PageResult;
import com.fssc.document.entity.OpArchive;
import com.fssc.framework.facade.dto.ArchiveInfoDTO;
import com.fssc.framework.facade.dto.ArchiveRegisterDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 档案中心 Facade：其他域通过此接口访问档案能力，
 * 不直接依赖 OpArchiveMapper / ArchiveDestroyApprovalMapper / ArchiveAccessLogMapper。
 *
 * 断点5：findLatestByRefId 替代 RequisitionWorkbenchService 直接查询 OpArchiveMapper
 * 断点6：findByBillId 替代 BillTraceChainController 直接查询 OpArchiveMapper
 * 断点8：registerBankSlipArchive 替代 BankReconcileService 直接插入 op_archive
 * 断点9：三个审批收尾方法替代 ArchiveBizAction 直接操作档案域三张表
 *
 * <p><b>分库同前</b>：档案域登记了独立路由键 {@code document}（{@code fssc.datasource.document}），
 * 独立库就绪前别名 master，实现类同时标注类级 {@code @FsscDataSource("document")}。
 * 共享作业页的档案登记/更新/分页/销毁原先直接注入 {@code OpArchiveMapper} 与档案域服务，
 * 现一并收进本接口（{@link #pageArchives}／{@link #createArchive}／{@link #updateArchive}／
 * {@link #requestDestroy}），仓储实现留在档案域内。
 */
public interface ArchiveFacade {

    /**
     * 查询关联单据的最新一条档案（断点5）。
     *
     * @param refId 关联单据 ID（op_archive.ref_id）
     * @return 最新档案 DTO，不存在时返回 null
     */
    ArchiveInfoDTO findLatestByRefId(Long refId);

    /**
     * 按单据 ID 查询档案列表（断点6，op_archive.bill_id 直挂）。
     *
     * <p>{@code doArchive} 归档时只写 ref_id、不写 bill_id，故并口收
     * {@code archive_type='BILL' AND ref_id=?}；两处都是本域内的裸主键，不跨域借用。
     *
     * @param billId 单据 ID
     * @return 档案 DTO 列表，按 ID 正序
     */
    List<ArchiveInfoDTO> findByBillId(Long billId);

    /**
     * 按审批流程实例反查档案（回溯列，非费用域联查唯一可用的档案关联点）。
     *
     * @param procInstIds 本单据各轮审批的流程实例 ID
     * @return 档案 DTO 列表，按 ID 正序；入参为空时返回空列表
     */
    List<ArchiveInfoDTO> findByProcInstIds(Collection<String> procInstIds);

    /**
     * 按档案主键取一行（「档案即单据」的域专用）。
     *
     * <p>归档审批流（FM_ARCHIVE）的 {@code bizId} 就是 {@code op_archive.id}，而该表的
     * {@code proc_inst_id} 存的是<b>来源单据</b>的审批流程实例、不是归档审批本身的流程实例 ⇒
     * 用本域流程回溯 {@link #findByProcInstIds} 必然 0 命中。主键取自身仍是本域内关联，
     * 不涉及跨域裸主键。
     *
     * @param archiveId 档案 ID
     * @return 档案 DTO，不存在时返回 null
     */
    ArchiveInfoDTO findById(Long archiveId);

    /**
     * 登记银行回单档案（断点8）。
     *
     * @param dto 登记参数
     * @return 新档案 ID
     */
    Long registerBankSlipArchive(ArchiveRegisterDTO dto);

    /**
     * 归档审批收尾：bizId = op_archive.id，审批通过置 ARCHIVED（断点9）。
     *
     * @param archiveId 档案 ID
     * @param approved 是否审批通过
     */
    void handleArchiveApproval(Long archiveId, boolean approved);

    /**
     * 销毁审批收尾：bizId = archive_destroy_approval.id，
     * 审批通过置审批单 APPROVED 并将涉及档案全部置 DESTROYED（断点9）。
     *
     * @param approvalId 销毁审批单 ID
     * @param approved 是否审批通过
     */
    void handleDestroyApproval(Long approvalId, boolean approved);

    /**
     * 借阅审批收尾：bizId = archive_access_log.id（断点9）。
     *
     * @param accessLogId 借阅单 ID
     * @param approved 是否审批通过
     */
    void handleBorrowApproval(Long accessLogId, boolean approved);

    /**
     * 带终审操作人的重载——引擎收尾线程无登录态，审批人只能由事件带回。
     * 默认回落两参行为（不写 approver_id），实现方覆盖后才落"谁批的"。
     *
     * @param endOperUserId 审批最终状态最后操作人 userId；null＝系统代做/取不到，留空不猜
     */
    default void handleBorrowApproval(Long accessLogId, boolean approved, Long endOperUserId) {
        handleBorrowApproval(accessLogId, approved);
    }

    // 域外读取（报表／联查／合规导出）

    /**
     * 全量档案清单（合规导出用；按 ID 正序，租户由拦截器收窄）。
     *
     * <p>不分页：导出器自己按列落 CSV，语义与原先 {@code selectList(orderByAsc id)} 一致。
     */
    List<OpArchive> findAllForExport();

    /**
     * 按单据 ID 或凭证 ID 集合取档案（单据全链路联查的归档段）。
     *
     * <p>谓词：{@code bill_id = billId OR voucher_id IN voucherIds}，创建时间倒序；
     * {@code voucherIds} 为空时只按 {@code bill_id} 查。
     *
     * @param billId 单据 ID
     * @param voucherIds 该单据各腿凭证 ID（可空集合）
     */
    List<OpArchive> findByBillIdOrVoucherIds(Long billId, Collection<Long> voucherIds);

    // 域外写入（共享作业页的档案箱）

    /**
     * 档案列表分页（共享作业「已归档档案」Tab）。
     *
     * <p>筛选口径与原先一致：{@code status} 入参按 {@code archive_status}（归档流程轴）筛，
     * 兼容老调用方传保管轴取值（FILED→ARCHIVED、DESTROYED→DISPOSED）；结果统一挂
     * 的归档流实例号/状态补充（只读 {@code wf_instance}）。
     */
    PageResult<OpArchive> pageArchives(long pageNum, long pageSize, String archiveType, String status);

    /**
     * 登记一卷档案（档案域内规则单点）。
     *
     * <p>本方法只做档案域自己的事：同号查重（已销毁的卷释放档案号＝物理删）、
     * {@code status} 镜像列按 {@code ArchiveService.dispositionOf} 兜底、{@code filingTime} 兜底、
     * 插入。<b>跨域前置</b>（凭证存在性校验、按来源单据回填 {@code procInstId}）由调用方完成后再传入。
     *
     * @param archive 档案行（{@code archiveNo} 必填）
     * @return 新档案 ID
     */
    Long createArchive(OpArchive archive);

    /** 更新档案（先做存在性校验；不存在抛 {@code BizException}）。 */
    void updateArchive(OpArchive archive);

    /**
     * 销毁唯一合规链入口——发起「销毁申请」（到期校验＋双人复核双签＋FM_DESTROY 起流程），
     * 本方法不直改任何销毁列。
     *
     * @param archiveIds 待销毁档案 ID
     * @param reason 申请理由
     * @param operatorId 申请人 userId（无登录态时传 null）
     * @return 销毁审批单 ID
     */
    Long requestDestroy(List<Long> archiveIds, String reason, Long operatorId);
}
