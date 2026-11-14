package com.fssc.masterdata.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdFormField;
import com.fssc.masterdata.entity.MdFormValidation;
import com.fssc.masterdata.service.FormMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单元数据引擎对外接口（闭环差距①「单据/界面不是元数据驱动」）。
 *
 * <p>路径 {@code /api/tenant/masterdata/form-meta/**}。
 *
 * <p><b>前端接入方式</b>：把写死的表单改成按元数据渲染。
 * <pre>
 *   const meta = await getFormMeta('EXP_CLAIM', roles) // 取字段定义（含必录/锁定）
 *   &lt;el-form :model="form" :rules="rulesFromMeta(meta)"&gt;
 *     &lt;component v-for="f in meta" :is="widgetOf(f.widgetType)" v-model="form[f.fieldKey]" /&gt;
 *   &lt;/el-form&gt;
 * </pre>
 * 新增一种单据/新增一个字段 = 后台插数据，**不改前端代码不发版**。
 */
@RestController
@RequestMapping("/api/tenant/masterdata/form-meta")
@RequiredArgsConstructor
public class FormMetaController {

    private final FormMetaService formMetaService;

    /**
     * 取表单字段元数据。
     *
     * @param formCode 表单编码
     * @param roles 当前用户角色码（逗号分隔，可空）。传了就会按 readonlyRoles/visibleRoles
     *                 把「锁定属性」在服务端就折叠好，前端不用再判断一次。
     */
    @GetMapping("/{formCode}/fields")
    public Result<List<MdFormField>> fields(@PathVariable String formCode,
                                            @RequestParam(required = false) String roles) {
        Set<String> roleSet = roles == null || roles.isBlank()
                ? Set.of() : Set.of(roles.split(","));
        return Result.success(formMetaService.fieldsOf(formCode, roleSet));
    }

    /** 取表单校验规则 */
    @GetMapping("/{formCode}/rules")
    public Result<List<MdFormValidation>> rules(@PathVariable String formCode,
                                                @RequestParam(required = false) String scope) {
        return Result.success(formMetaService.rulesOf(formCode, scope));
    }

    /**
     * 服务端权威校验（前端绕过直调接口也拦得住）。
     *
     * @param formCode 表单编码
     * @param scope 校验时机 SAVE/APPROVE
     * @param data 提交数据
     */
    @PostMapping("/{formCode}/validate")
    public Result<FormMetaService.ValidateResult> validate(
            @PathVariable String formCode,
            @RequestParam(defaultValue = "SAVE") String scope,
            @RequestBody Map<String, Object> data) {
        return Result.success(formMetaService.validate(formCode, data, scope));
    }

    /** 按元数据填默认值 */
    @PostMapping("/{formCode}/defaults")
    public Result<Map<String, Object>> defaults(@PathVariable String formCode,
                                                @RequestBody(required = false) Map<String, Object> data) {
        return Result.success(formMetaService.applyDefaults(formCode, data));
    }

    /**
     * 元数据总览（字段 + 规则），前端一次拿全，避免两次往返。
     */
    @GetMapping("/{formCode}/bundle")
    public Result<Map<String, Object>> bundle(@PathVariable String formCode,
                                             @RequestParam(required = false) String roles,
                                             @RequestParam(required = false) String scope) {
        Set<String> roleSet = roles == null || roles.isBlank()
                ? Set.of() : Set.of(roles.split(","));
        return Result.success(Map.of(
                "formCode", formCode,
                "fields", formMetaService.fieldsOf(formCode, roleSet),
                "rules", formMetaService.rulesOf(formCode, scope)));
    }

    // 字段维护（配置面）

    @PostMapping("/field/save")
    @RequiresPerm("md:formmeta:field:edit")
    @OperLog(module = "表单元数据", operType = "UPDATE", desc = "保存表单字段元数据")
    public Result<Long> saveField(@RequestBody MdFormField field) {
        // 复用 mapper 直存：字段元数据本身是配置数据，无需额外业务规则
        // 为保持本 Controller 轻量，委托 service 层的默认实现
        return Result.success(formMetaService.saveField(field));
    }

    @PostMapping("/field/delete/{fieldId}")
    @RequiresPerm("md:formmeta:field:edit")
    @OperLog(module = "表单元数据", operType = "DELETE", desc = "删除表单字段元数据")
    public Result<Void> deleteField(@PathVariable Long fieldId) {
        formMetaService.deleteField(fieldId);
        return Result.success();
    }
}
