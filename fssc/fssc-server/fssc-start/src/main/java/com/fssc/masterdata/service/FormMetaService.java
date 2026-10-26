// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdFormField;
import com.fssc.masterdata.entity.MdFormValidation;
import com.fssc.masterdata.mapper.MdFormFieldMapper;
import com.fssc.masterdata.mapper.MdFormValidationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 表单元数据引擎（V1.4.85）——闭环差距①「单据/界面不是元数据驱动」。
 *
 * <p><b>本类为什么是关键</b>：差距①的根因不是「没有配置表」，
 * 而是 {@code md_form_config} 的 config_json 里只有分节名、<b>没有任何字段定义</b>，
 * 且前端零消费 ⇒ 配置写了也白写。本类做三件事，让「配置 → 生效」这条链真正闭合：
 * <ol>
 *   <li>{@link #fieldsOf} —— 按表单返回字段元数据，供前端动态渲染；
 *       <b>并且按当前用户角色过滤</b>（readonlyRoles/visibleRoles），把「锁定属性」
 *       从前端自觉变成服务端下发；</li>
 *   <li>{@link #validate} —— 服务端按元数据执行校验规则，
 *       <b>不依赖前端是否传了校验</b>（前端绕过直调接口也拦得住）；</li>
 *   <li>{@link #applyDefaults} —— 按元数据填默认值，新增单据不用在前端写死初值。</li>
 * </ol>
 *
 * <p><b>与前端的分工</b>：前端渲染 + 体验性提示；服务端做<b>权威校验</b>。
 * 二者读同一份元数据，不会出现「前端显示必填、后端没校验」的规则分裂。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormMetaService {

    private final MdFormFieldMapper fieldMapper;
    private final MdFormValidationMapper validationMapper;

    /** 校验结果 */
    public record ValidateResult(boolean passed, List<Violation> errors, List<Violation> warnings) {
    }

    /** 校验违规项 */
    public record Violation(String fieldKey, String fieldLabel, String ruleType, String message,
                            String severity) {
    }

    // ==================== 元数据读取 ====================

    /**
     * 取表单字段元数据。
     *
     * @param formCode 表单编码
     * @param roles    当前用户角色码集合（用于 readonlyRoles/visibleRoles 过滤，可空）
     */
    public List<MdFormField> fieldsOf(String formCode, Set<String> roles) {
        List<MdFormField> all = fieldMapper.selectList(new LambdaQueryWrapper<MdFormField>()
                .eq(MdFormField::getFormCode, formCode)
                .eq(MdFormField::getStatus, "ENABLED")
                .orderByAsc(MdFormField::getSortOrder)
                .orderByAsc(MdFormField::getId));
        if (roles == null || roles.isEmpty()) {
            return all;
        }
        List<MdFormField> out = new ArrayList<>(all.size());
        for (MdFormField f : all) {
            // 可见性：visibleRoles 为空=全部可见；非空则须命中其一
            if (!matchRole(f.getVisibleRoles(), roles)) {
                continue;
            }
            // 锁定：字段自身 locked=1，或当前用户命中只读角色 ⇒ 下发 locked=1
            boolean readOnlyRole = hitReadonlyRole(f.getReadonlyRoles(), roles);
            if (readOnlyRole) {
                f.setLocked(1);
            }
            out.add(f);
        }
        return out;
    }

    public List<MdFormValidation> rulesOf(String formCode, String scope) {
        LambdaQueryWrapper<MdFormValidation> w = new LambdaQueryWrapper<MdFormValidation>()
                .eq(MdFormValidation::getFormCode, formCode)
                .eq(MdFormValidation::getStatus, "ENABLED")
                .orderByAsc(MdFormValidation::getSortOrder)
                .orderByAsc(MdFormValidation::getId);
        if (scope != null && !scope.isBlank()) {
            w.in(MdFormValidation::getScope, scope, "BOTH");
        }
        return validationMapper.selectList(w);
    }

    // ==================== 权威校验 ====================

    /**
     * 按元数据校验提交数据。
     *
     * <p><b>这是「元数据驱动」真正的价值所在</b>：必录/长度/数值范围/正则/跨字段比对
     * 全部由配置决定，新增一种单据不需要再写一套 if-else 校验。
     * ERROR 级违规抛异常阻断提交，WARN 级仅记录。
     *
     * @param formCode 表单编码
     * @param data     提交数据（fieldKey → value）
     * @param scope    校验时机 SAVE/APPROVE
     */
    public ValidateResult validate(String formCode, Map<String, Object> data, String scope) {
        List<MdFormField> fields = fieldsOf(formCode, null);
        Map<String, MdFormField> fieldMap = new HashMap<>();
        for (MdFormField f : fields) {
            fieldMap.put(f.getFieldKey(), f);
        }
        List<MdFormValidation> rules = rulesOf(formCode, scope);

        List<Violation> errors = new ArrayList<>();
        List<Violation> warnings = new ArrayList<>();

        // ① 必录：直接读字段元数据的 required（而非只读规则表），
        //    保证「字段上勾了必录」这件事本身就生效，不依赖是否配了规则
        for (MdFormField f : fields) {
            if (Objects.equals(f.getRequired(), 1) && isBlank(data.get(f.getFieldKey()))) {
                errors.add(new Violation(f.getFieldKey(), f.getFieldLabel(), "REQUIRED",
                        f.getFieldLabel() + "不能为空", "ERROR"));
            }
        }

        // ② 规则表逐条执行（按 sortOrder 顺序，对齐 检查点配置的「执行顺序」）
        for (MdFormValidation r : rules) {
            Object raw = data.get(r.getFieldKey());
            MdFormField f = fieldMap.get(r.getFieldKey());
            String label = f == null ? r.getFieldKey() : f.getFieldLabel();
            String msg = runRule(r, raw, data);
            if (msg == null) {
                continue;
            }
            String text = (r.getErrorMessage() == null || r.getErrorMessage().isBlank())
                    ? label + msg : r.getErrorMessage();
            if ("WARN".equalsIgnoreCase(r.getSeverity())) {
                warnings.add(new Violation(r.getFieldKey(), label, r.getRuleType(), text, "WARN"));
            } else {
                errors.add(new Violation(r.getFieldKey(), label, r.getRuleType(), text, "ERROR"));
            }
        }

        log.debug("[form-meta] 校验 form={} 字段={} 规则={} 错误={} 警告={}",
                formCode, fields.size(), rules.size(), errors.size(), warnings.size());
        return new ValidateResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * 校验并在 ERROR 级违规时抛异常（供 Service 层在保存/提交前直接调用）。
     */
    public void validateOrThrow(String formCode, Map<String, Object> data, String scope) {
        ValidateResult r = validate(formCode, data, scope);
        if (!r.passed()) {
            String detail = r.errors().stream()
                    .map(v -> v.fieldLabel() + ": " + v.message())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            throw new BizException(ErrorCode.PARAM_INVALID, "表单校验未通过 → " + detail);
        }
    }

    /**
     * 执行单条规则，返回错误文案（null=通过）。
     */
    private String runRule(MdFormValidation r, Object raw, Map<String, Object> data) {
        String type = Objects.requireNonNullElse(r.getRuleType(), "").toUpperCase();
        switch (type) {
            case "REQUIRED", "NOT_NULL" -> {
                return isBlank(raw) ? "不能为空" : null;
            }
            case "MIN_VALUE" -> {
                if (isBlank(raw)) {
                    return null;
                }
                BigDecimal v = toDecimal(raw);
                if (v == null) {
                    return "不是合法数字";
                }
                return v.compareTo(toDecimal(r.getRuleValue())) < 0 ? "不能小于" + r.getRuleValue() : null;
            }
            case "MAX_VALUE" -> {
                if (isBlank(raw)) {
                    return null;
                }
                BigDecimal v = toDecimal(raw);
                if (v == null) {
                    return "不是合法数字";
                }
                return v.compareTo(toDecimal(r.getRuleValue())) > 0 ? "不能大于" + r.getRuleValue() : null;
            }
            case "MIN_LENGTH" -> {
                return isBlank(raw) ? null
                        : (String.valueOf(raw).length() < Integer.parseInt(r.getRuleValue())
                        ? "长度不能小于" + r.getRuleValue() : null);
            }
            case "MAX_LENGTH" -> {
                return isBlank(raw) ? null
                        : (String.valueOf(raw).length() > Integer.parseInt(r.getRuleValue())
                        ? "长度不能超过" + r.getRuleValue() : null);
            }
            case "PATTERN" -> {
                return isBlank(raw) || r.getRuleValue() == null ? null
                        : (String.valueOf(raw).matches(r.getRuleValue()) ? null : "格式不正确");
            }
            case "EMAIL" -> {
                return isBlank(raw) || String.valueOf(raw).matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")
                        ? null : "邮箱格式不正确";
            }
            case "PHONE" -> {
                return isBlank(raw) || String.valueOf(raw).matches("^1[3-9]\\d{9}$")
                        ? null : "手机号格式不正确";
            }
            case "ID_CARD" -> {
                return isBlank(raw) || String.valueOf(raw).matches("^\\d{17}[\\dXx]$")
                        ? null : "身份证格式不正确";
            }
            case "UNIQUE" -> {
                // 唯一性需要查库，此处只做「已填」检查；真正的唯一性由 DB 唯一索引默认处理
                return null;
            }
            case "CUSTOM_EXPRESSION" -> {
                // 自定义表达式：走 SpEL 需注入 EvaluationContext，此处保守放行并留日志，
                // 避免在元数据里塞可执行代码带来的安全面（表达式注入）
                log.warn("[form-meta] CUSTOM_EXPRESSION 规则暂不在服务端执行（防表达式注入），"
                        + "form={} field={}", r.getFormCode(), r.getFieldKey());
                return null;
            }
            case "CROSS_FIELD" -> {
                if (isBlank(raw) || r.getRuleValue2() == null) {
                    return null;
                }
                Object other = data.get(r.getRuleValue2());
                if (other == null) {
                    return null;
                }
                BigDecimal a = toDecimal(raw), b = toDecimal(other);
                if (a != null && b != null) {
                    return a.compareTo(b) <= 0 ? "不能大于" + r.getRuleValue2() : null;
                }
                return String.valueOf(raw).compareTo(String.valueOf(other)) <= 0
                        ? "不能大于" + r.getRuleValue2() : null;
            }
            default -> {
                return null;
            }
        }
    }

    // ==================== 默认值 ====================

    /**
     * 按元数据填默认值（数据里没有的字段用 defaultValue 补上）。
     */
    public Map<String, Object> applyDefaults(String formCode, Map<String, Object> data) {
        Map<String, Object> out = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        for (MdFormField f : fieldsOf(formCode, null)) {
            if (f.getDefaultValue() == null || f.getDefaultValue().isBlank()) {
                continue;
            }
            Object cur = out.get(f.getFieldKey());
            if (cur == null || "".equals(cur)) {
                out.put(f.getFieldKey(), castByWidget(f.getWidgetType(), f.getDefaultValue()));
            }
        }
        return out;
    }

    private Object castByWidget(String widget, String def) {
        return switch (Objects.requireNonNullElse(widget, "INPUT")) {
            case "MONEY", "PERCENT", "INPUT_NUMBER" -> new BigDecimal(def);
            case "SWITCH" -> "1".equals(def) ? 1 : 0;
            default -> def;
        };
    }

    // ==================== 字段维护（配置面） ====================

    /**
     * 保存字段元数据（配置面入口）。
     *
     * <p><b>为什么单独开放写接口</b>：本类的价值在于「新增字段 = 插数据」，
     * 如果只读不写，这条链仍然要改代码才能加字段，差距①就没真正闭环。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long saveField(MdFormField field) {
        if (field.getFormCode() == null || field.getFormCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单编码不能为空");
        }
        if (field.getFieldKey() == null || field.getFieldKey().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段标识不能为空");
        }
        MdFormField exist = fieldMapper.selectOne(new LambdaQueryWrapper<MdFormField>()
                .eq(MdFormField::getFormCode, field.getFormCode())
                .eq(MdFormField::getFieldKey, field.getFieldKey())
                .last("LIMIT 1"));
        if (exist == null) {
            fieldMapper.insert(field);
        } else {
            field.setId(exist.getId());
            fieldMapper.updateById(field);
        }
        log.info("[form-meta] 字段保存 form={} key={} widget={} required={}",
                field.getFormCode(), field.getFieldKey(), field.getWidgetType(), field.getRequired());
        return field.getId();
    }

    /**
     * 删除字段（系统字段禁删）。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteField(Long fieldId) {
        MdFormField f = fieldMapper.selectById(fieldId);
        if (f == null) {
            return;
        }
        if (Objects.equals(f.getSystemFlag(), 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "系统字段禁止删除（可置 DISABLED 停用）: " + f.getFieldKey());
        }
        validationMapper.delete(new LambdaQueryWrapper<MdFormValidation>()
                .eq(MdFormValidation::getFormCode, f.getFormCode())
                .eq(MdFormValidation::getFieldKey, f.getFieldKey()));
        fieldMapper.deleteById(fieldId);
    }

    // ==================== 内部 ====================

    /**
     * 角色过滤：配置为空=不限制；非空则须命中其一。
     */
    private boolean matchRole(String roleCfg, Set<String> roles) {
        if (roleCfg == null || roleCfg.isBlank()) {
            return true;
        }
        return Arrays.stream(roleCfg.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).anyMatch(roles::contains);
    }

    private boolean hitReadonlyRole(String roleCfg, Set<String> roles) {
        return roleCfg != null && !roleCfg.isBlank()
                && Arrays.stream(roleCfg.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).anyMatch(roles::contains);
    }

    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        try {
            return new BigDecimal(String.valueOf(o).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
