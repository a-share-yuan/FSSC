package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdExchangeRate;
import com.fssc.masterdata.service.ExchangeRateService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 主数据 ExchangeRate（/api/tenant/masterdata/exchange-rate/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/exchange-rate")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService service;

    @RequiresPerm("md:exchangerate:list")
    @GetMapping("/page")
    public Result<PageResult<MdExchangeRate>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:exchangerate:create")
    @OperLog(module = "EXCHANGE_RATE", operType = "INSERT", desc = "新增ExchangeRate")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdExchangeRate e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:exchangerate:update")
    @OperLog(module = "EXCHANGE_RATE", operType = "UPDATE", desc = "更新ExchangeRate")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdExchangeRate e) {
        service.update(e);
        return Result.success();
    }

    /** 汇率查询（海外报账①）：直查→反向倒数→经 CNY 交叉；无价返回 data=null，前端据此放行手填 */
    @RequiresPerm("md:exchangerate:list")
    @GetMapping("/lookup")
    public Result<BigDecimal> lookup(@RequestParam String from, @RequestParam String to,
                                     @RequestParam String date) {
        return Result.success(service.lookup(from, to, LocalDate.parse(date)));
    }

    /** 月度导入 CSV 模板下载（UTF-8 BOM，Excel 直接打开不乱码） */
    @RequiresPerm("md:exchangerate:list")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = ("\uFEFF" + ExchangeRateService.CSV_TEMPLATE + "\n").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=exchange_rate_template.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** 月度汇率 CSV 批量导入（列 from,to,rate,effect_date,expire_date；按 租户+from+to+effect_date upsert） */
    @RequiresPerm("md:exchangerate:create")
    @OperLog(module = "EXCHANGE_RATE", operType = "IMPORT", desc = "月度汇率CSV导入")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Integer> importCsv(@RequestParam("file") MultipartFile file) throws Exception {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Result.success(service.importCsv(text));
    }

    // 权限治理（2026-09-16）：原用 @RequiresPerm("md:rate:delete")，该码挂在菜单「汇率删除」(#3464)
    // 且 **0 角色持有** → 本接口对所有角色恒为无权。现改用对象一致的 md:exchangerate:delete，
    // 并由 seed-perm-p1-20260916.sql 将该码授予数据配置管理员。旧码持有者为 0，故不留 OR 过渡。
    // 同族命名分叉另见菜单 #211「汇率管理」挂 md:exchange-rate:list（死码）而接口实际要求
    // md:exchangerate:list，已由同一脚本一并对齐。
    @RequiresPerm("md:exchangerate:delete")
    @OperLog(module = "EXCHANGE_RATE", operType = "DELETE", desc = "删除汇率")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
