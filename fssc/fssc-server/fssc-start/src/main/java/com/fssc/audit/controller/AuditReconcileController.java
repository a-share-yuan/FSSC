package com.fssc.audit.controller;

import com.fssc.audit.entity.AuditReconcile;
import com.fssc.audit.service.ReconcileService;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 数据一致性对账。 */
@RestController
@RequestMapping("/api/tenant/audit/reconcile")
@RequiredArgsConstructor
public class AuditReconcileController {

    private final ReconcileService reconcileService;

    @RequiresPerm("audit:reconcile:list")
    @GetMapping("/page")
    public Result<PageResult<AuditReconcile>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   String billType, String status) {
        return Result.success(reconcileService.page(pageNum, pageSize, billType, status));
    }

    @RequiresPerm("audit:reconcile:list")
    @PostMapping("/run")
    public Result<Integer> run() {
        return Result.success(reconcileService.runReconcile());
    }

    @RequiresPerm("audit:reconcile:list")
    @PostMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body == null ? null : (String) body.get("remark");
        reconcileService.resolve(id, remark);
        return Result.success();
    }
}
