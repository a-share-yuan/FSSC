// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.facade.dto.BillArchiveDTO;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.document.entity.OpArchive;
import com.fssc.sharing.entity.OpBankStatement;
import com.fssc.sharing.entity.OpQuality;
import com.fssc.document.service.BillArchiveService;
import com.fssc.sharing.service.SharingEnhanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 共享运营增强（/api/tenant/sharing/enhance/**）：质检 + 档案 + 银行流水。 */
@RestController
@RequestMapping("/api/tenant/sharing/enhance")
@RequiredArgsConstructor
public class SharingEnhanceController {

    private final SharingEnhanceService enhanceService;
    private final BillArchiveService archiveService;

    // ---------- 质检 ----------

    @RequiresPerm("op:quality:list")
    @GetMapping("/quality/page")
    public Result<PageResult<OpQuality>> qualityPage(PageQuery query,
                                                     @RequestParam(required = false) Long billId,
                                                     @RequestParam(required = false) String result) {
        return Result.success(enhanceService.qualityPage(query.getPageNum(), query.getPageSize(), billId, result));
    }

    @RequiresPerm("op:quality:create")
    @OperLog(module = "OP_QUALITY", operType = "INSERT", desc = "质检记录")
    @PostMapping("/quality/create")
    public Result<Long> qualityCreate(@RequestBody OpQuality q) {
        return Result.success(enhanceService.addQuality(q));
    }

    @RequiresPerm("op:quality:update")
    @OperLog(module = "OP_QUALITY", operType = "UPDATE", desc = "更新质检")
    @PostMapping("/quality/update")
    public Result<Void> qualityUpdate(@RequestBody OpQuality q) {
        enhanceService.updateQuality(q);
        return Result.success();
    }

    @RequiresPerm("op:quality:delete")
    @OperLog(module = "OP_QUALITY", operType = "DELETE", desc = "删除质检")
    @PostMapping("/quality/{id}/delete")
    public Result<Void> qualityDelete(@PathVariable Long id) {
        enhanceService.deleteQuality(id);
        return Result.success();
    }

    // ---------- 档案归档 ----------

    @RequiresPerm("op:archive:list")
    @GetMapping("/archive/page")
    public Result<PageResult<OpArchive>> archivePage(PageQuery query,
                                                     @RequestParam(required = false) String archiveType,
                                                     @RequestParam(required = false) String status) {
        return Result.success(enhanceService.archivePage(query.getPageNum(), query.getPageSize(), archiveType, status));
    }

    /** 归档清单（审批完成+影像完整）：archiveStatus 空＝未归档、ARCHIVED＝已归档、ALL＝不限（D-DEC-276b/B1） */
    @RequiresPerm("op:archive:list")
    @GetMapping("/archive/pending")
    public Result<PageResult<BillArchiveDTO>> archivePending(PageQuery query,
                                                                              @RequestParam(required = false) String billType,
                                                                              @RequestParam(required = false) String keyword,
                                                                              @RequestParam(required = false) String archiveStatus) {
        return Result.success(archiveService.pendingBills(query.getPageNum(), query.getPageSize(),
                billType, keyword, archiveStatus));
    }

    /** 归档执行：迁移文件到 archive 区 + 建档案 + 标记单据 */
    @RequiresPerm("op:archive:create")
    @OperLog(module = "OP_ARCHIVE", operType = "INSERT", desc = "单据批量归档")
    @PostMapping("/archive/do-archive")
    public Result<Integer> doArchive(@RequestBody java.util.List<Long> billIds) {
        return Result.success(archiveService.doArchive(billIds));
    }

    @RequiresPerm("op:archive:create")
    @OperLog(module = "OP_ARCHIVE", operType = "INSERT", desc = "档案归档")
    @PostMapping("/archive/create")
    public Result<Long> archiveCreate(@RequestBody OpArchive a) {
        return Result.success(enhanceService.createArchive(a));
    }

    @RequiresPerm("op:archive:update")
    @OperLog(module = "OP_ARCHIVE", operType = "UPDATE", desc = "更新档案")
    @PostMapping("/archive/update")
    public Result<Void> archiveUpdate(@RequestBody OpArchive a) {
        enhanceService.updateArchive(a);
        return Result.success();
    }

    /**
     * R5-D-197（内控红线，销毁不可逆）：改前直调 {@code enhanceService.destroyArchive(id)} 一键置 DISPOSED
     * （无到期校验、无销毁审批、不起 FM_DESTROY）——同一缺陷的第二条人工旁路。现整体改道：
     * 一律转 ArchiveDestroyService 发起「销毁申请」，由合规链（到期校验＋双签＋FM_DESTROY）定论；
     * 理由缺失时由服务层按「请填写销毁理由」拒绝（fail-closed），不再存在"无理由一键销毁"。
     */
    @RequiresPerm("op:archive:update")
    @OperLog(module = "OP_ARCHIVE", operType = "INSERT", desc = "发起档案销毁申请")
    @PostMapping("/archive/{id}/destroy")
    public Result<Long> archiveDestroy(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        return Result.success(enhanceService.destroyArchive(id, reason));
    }

    // ---------- 银行流水 ----------

    @RequiresPerm("op:bank:list")
    @GetMapping("/statement/page")
    public Result<PageResult<OpBankStatement>> statementPage(PageQuery query,
                                                             @RequestParam(required = false) Long bankAccountId,
                                                             @RequestParam(required = false) String reconStatus,
                                                             // 161b（筛选透传环节）：SharingStatement.vue 一直在发 bankSerial（流水号）
                                                             // 与 transType（交易方向 借/贷），本方法此前只声明前两个入参 ⇒ 两参被 Spring 直接丢弃，
                                                             // 查询条件"看着填了、结果没变"（静默哑火，零报错）。补进签名并透传给 service。
                                                             @RequestParam(required = false) String bankSerial,
                                                             @RequestParam(required = false) String transType) {
        return Result.success(enhanceService.statementPage(query.getPageNum(), query.getPageSize(),
                bankAccountId, reconStatus, bankSerial, transType));
    }

    @RequiresPerm("op:bank:import")
    @OperLog(module = "OP_BANK_STATEMENT", operType = "IMPORT", desc = "导入银行流水(单条)")
    @PostMapping(value = "/statement/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Void> statementImport(@RequestBody OpBankStatement s) {
        enhanceService.importStatement(s);
        return Result.success();
    }

    @RequiresPerm("op:bank:import")
    @OperLog(module = "OP_BANK_STATEMENT", operType = "IMPORT", desc = "批量导入银行流水")
    @PostMapping(value = "/statement/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> statementImportCsv(@RequestParam("file") MultipartFile file,
                                                          @RequestParam("bankAccountId") Long bankAccountId) {
        return Result.success(enhanceService.importStatementCsv(file, bankAccountId));
    }

    @RequiresPerm("op:bank:update")
    @OperLog(module = "OP_BANK_STATEMENT", operType = "UPDATE", desc = "流水对账")
    @PostMapping("/statement/{id}/recon")
    public Result<Void> statementRecon(@PathVariable Long id, @RequestParam Long reconBillId) {
        enhanceService.markRecon(id, reconBillId);
        return Result.success();
    }

    @RequiresPerm("op:bank:delete")
    @OperLog(module = "OP_BANK_STATEMENT", operType = "DELETE", desc = "删除流水")
    @PostMapping("/statement/{id}/delete")
    public Result<Void> statementDelete(@PathVariable Long id) {
        enhanceService.deleteStatement(id);
        return Result.success();
    }
}
