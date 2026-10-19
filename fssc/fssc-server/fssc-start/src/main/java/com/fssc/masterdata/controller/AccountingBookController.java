package com.fssc.masterdata.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.GlAccountingBook;
import com.fssc.masterdata.entity.GlBookSubjectMap;
import com.fssc.masterdata.entity.GlCurrency;
import com.fssc.masterdata.entity.GlStandard;
import com.fssc.masterdata.service.AccountingBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多账簿 / 多准则 / 多本位币 对外接口（V1.4.87，闭环差距③）。
 *
 * <p>路径 {@code /api/tenant/masterdata/book/**}。
 */
@RestController
@RequestMapping("/api/tenant/masterdata/book")
@RequiredArgsConstructor
public class AccountingBookController {

    private final AccountingBookService bookService;

    // ==================== 基础配置 ====================

    /** 会计准则列表 */
    @GetMapping("/standard/list")
    public Result<List<GlStandard>> standards() {
        return Result.success(bookService.listStandards());
    }

    /** 币种列表（含本位币标记） */
    @GetMapping("/currency/list")
    public Result<List<GlCurrency>> currencies() {
        return Result.success(bookService.listCurrencies());
    }

    // ==================== 账簿 ====================

    /** 账簿列表（可按年度/类型过滤） */
    @GetMapping("/list")
    public Result<List<GlAccountingBook>> list(@RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) String bookType) {
        return Result.success(bookService.listBooks(year, bookType));
    }

    /** 按核算组织列全部账簿（多准则 ⇒ 可能多本主账簿） */
    @GetMapping("/company/{companyId}")
    public Result<List<GlAccountingBook>> byCompany(@PathVariable Long companyId,
                                                    @RequestParam(required = false) Integer year) {
        return Result.success(bookService.listBooksOf(companyId, year));
    }

    /**
     * 多账簿总览（报表页「按不同会计准则出具多套财务核算数据」的数据源）。
     */
    @GetMapping("/overview")
    public Result<List<Map<String, Object>>> overview(@RequestParam(required = false) Integer year) {
        return Result.success(bookService.overview(year));
    }

    /**
     * 建账。
     *
     * <p>同一核算组织在同一准则与年度下只能有一本同类型账簿（主/副账簿），
     * 应用层校验给可读报错 + {@code uk_gab_main} 唯一索引防并发穿透。
     */
    @PostMapping("/create")
    @RequiresPerm("md:book:edit")
    @OperLog(module = "会计账簿", operType = "INSERT", desc = "建立账簿")
    public Result<Long> create(@RequestBody GlAccountingBook book) {
        return Result.success(bookService.createBook(book));
    }

    // ==================== 科目映射 ====================

    /** 取账簿科目映射（key=本准则科目ID，value=通用科目ID） */
    @GetMapping("/{bookId}/subject-map")
    public Result<Map<Long, Long>> subjectMap(@PathVariable Long bookId) {
        return Result.success(bookService.subjectMapOf(bookId));
    }

    /** 按准则归一科目（跨账簿汇总时用） */
    @GetMapping("/{bookId}/map-subject/{subjectId}")
    public Result<Long> mapSubject(@PathVariable Long bookId, @PathVariable Long subjectId) {
        return Result.success(bookService.mapSubject(bookId, subjectId));
    }

    /** 保存科目映射 */
    @PostMapping("/subject-map/save")
    @RequiresPerm("md:book:edit")
    @OperLog(module = "会计账簿", operType = "UPDATE", desc = "保存账簿科目映射")
    public Result<Long> saveSubjectMap(@RequestBody GlBookSubjectMap map) {
        return Result.success(bookService.saveSubjectMap(map));
    }
}
