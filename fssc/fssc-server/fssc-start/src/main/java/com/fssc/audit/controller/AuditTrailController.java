package com.fssc.audit.controller;

import com.fssc.audit.entity.AuditTrail;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 审计记录：分页查询 + 按单据导出 CSV + 内部记录。 */
@RestController
@RequestMapping("/api/tenant/audit/trail")
@RequiredArgsConstructor
public class AuditTrailController {

    private final AuditTrailService trailService;

    @RequiresPerm("audit:trail:list")
    @GetMapping("/page")
    public Result<PageResult<AuditTrail>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               String bizType, String bizNo, String keyword) {
        return Result.success(trailService.page(pageNum, pageSize, bizType, bizNo, keyword));
    }

    /** 导出审计轨迹 CSV：bizNo/bizType/keyword 可选，全空则导出最近 5000 条 */
    @RequiresPerm("audit:trail:list")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String bizNo,
                       @RequestParam(required = false) String bizType,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<AuditTrail> list = trailService.listByFilter(bizType, bizNo, keyword, 5000);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("时间,业务类型,业务单号,动作,变更对象,对象ID,操作人,变更前,变更后,说明\n");
        for (AuditTrail t : list) {
            sb.append(ts(t.getCreateTime())).append(',')
                    .append(csv(t.getBizType())).append(',')
                    .append(csv(t.getBizNo())).append(',')
                    .append(csv(t.getAction())).append(',')
                    .append(csv(t.getObjType())).append(',')
                    .append(csv(t.getObjId())).append(',')
                    .append(csv(t.getOperatorName() != null ? t.getOperatorName()
                            : (t.getOperator() == null ? "系统" : String.valueOf(t.getOperator())))).append(',')
                    .append(csv(shortJson(t.getBeforeJson()))).append(',')
                    .append(csv(shortJson(t.getAfterJson()))).append(',')
                    .append(csv(t.getRemark())).append('\n');
        }
        response.setContentType("text/csv;charset=UTF-8");
        String name = (bizNo == null || bizNo.isBlank()) ? "audit_trail" : "audit_" + bizNo.replaceAll("[^A-Za-z0-9_\\-.]", "_");
        response.setHeader("Content-Disposition", "attachment;filename=" + name + ".csv");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 内部记录变更（供业务域调用；权限注解放宽为任意登录态） */
    @PostMapping("/record")
    public Result<Void> record(@RequestBody Map<String, Object> body) {
        trailService.record(
                (String) body.get("bizType"),
                body.get("bizId") == null ? null : ((Number) body.get("bizId")).longValue(),
                (String) body.get("bizNo"),
                (String) body.get("objType"),
                body.get("objId") == null ? null : String.valueOf(body.get("objId")),
                (String) body.get("action"),
                (String) body.get("beforeJson"),
                (String) body.get("afterJson"),
                (String) body.get("remark"));
        return Result.success();
    }

    private static String ts(LocalDateTime t) {
        return t == null ? "" : t.toString().replace("T", " ");
    }

    private static String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String shortJson(String v) {
        if (v == null || v.length() <= 300) return v == null ? "" : v;
        return v.substring(0, 300) + "...";
    }
}
