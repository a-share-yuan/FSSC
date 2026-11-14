package com.fssc.form.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.form.entity.BizFormBill;
import com.fssc.form.service.FormBillService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 可配置表单的通用单据入口（{@code /api/tenant/form/**}）。
 *
 * <p>一套接口服务所有客户自定义的表单：表单编码由路径传入，字段与规则从元数据取，
 * 新增一张单据不新增任何 Controller / Service / 路由。
 */
@RestController
@RequestMapping("/api/tenant/form")
@RequiredArgsConstructor
public class FormBillController {

    private final FormBillService formBillService;

    /** 运行时元数据：表单头 + 字段 + 校验规则 */
    @GetMapping("/meta/{formCode}")
    public Result<Map<String, Object>> meta(@PathVariable String formCode,
                                            @RequestParam(required = false) String roles) {
        return Result.success(formBillService.runtimeMeta(formCode, roles));
    }

    /** 保存草稿（新建时 id 为空） */
    @RequiresPerm("form:bill:save")
    @OperLog(module = "FORM_BILL", operType = "INSERT", desc = "保存通用单据")
    @PostMapping("/bill/{formCode}/save")
    public Result<Long> save(@PathVariable String formCode, @RequestBody SaveReq req) {
        return Result.success(formBillService.save(formCode, req.getId(), req.getData()));
    }

    /** 提交审批：按配置的流程 key 起流 */
    @RequiresPerm("form:bill:submit")
    @OperLog(module = "FORM_BILL", operType = "SUBMIT", desc = "提交通用单据")
    @PostMapping("/bill/submit/{id}")
    public Result<String> submit(@PathVariable Long id) {
        return Result.success(formBillService.submit(id));
    }

    /** 单据详情（含已解析的表单数据） */
    @GetMapping("/bill/detail/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(formBillService.detail(id));
    }

    /** 我发起的单据 */
    @GetMapping("/bill/my")
    public Result<PageResult<BizFormBill>> my(@RequestParam(required = false) String formCode,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(formBillService.myPage(formCode, status, pageNum, pageSize));
    }

    @Data
    public static class SaveReq {
        /** 单据ID；为空表示新建 */
        private Long id;
        /** 表单字段键值，键取自 md_form_field.field_key */
        private Map<String, Object> data;
    }
}
