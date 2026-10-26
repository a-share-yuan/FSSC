// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.Result;
import com.fssc.contract.entity.ContContract;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同导入导出（需求 5.2/5.5：导出合同模板、导入历史合同）。
 * CSV 格式（UTF-8 BOM）：合同编号,合同名称,合同类型,乙方,金额,签订公司ID,费用类型ID,开始日期,结束日期,备注
 */
@RestController
@RequestMapping("/api/tenant/contract")
@RequiredArgsConstructor
public class ContractImportController {

    private final ContContractMapper contractMapper;
    private final com.fssc.framework.org.OrgGuard orgGuard;

    @RequiresPerm("contract:import")
    @PostMapping("/template")
    public Result<String> template() {
        String header = "合同编号,合同名称,合同类型,乙方名称,合同金额,签订公司ID,费用类型ID,开始日期,结束日期,备注\n" +
                "HT2026-DEMO,示例服务合同,SERVICE,示例乙方,100000.00,1,5,2026-01-01,2026-12-31,历史合同导入示例\n";
        return Result.success(header);
    }

    @RequiresPerm("contract:import")
    @OperLog(module = "CONTRACT", operType = "INSERT", desc = "批量导入历史合同")
    @PostMapping("/import")
    public Result<Integer> importContracts(@RequestBody String csv) {
        if (csv == null || csv.isBlank()) {
            return Result.fail(400, "导入内容为空");
        }
        // 去 BOM
        if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1);
        }
        String[] lines = csv.split("\\r?\\n");
        Long tenantId = TenantContext.getTenantId();
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            if (i == 0 && line.startsWith("合同编号")) continue; // 跳过表头
            String[] f = line.split(",");
            if (f.length < 6) {
                errors.add("第" + (i + 1) + "行列数不足");
                continue;
            }
            // 必填项行级校验：导入通道与表单同一规则，否则空合同号/空名称会绕过一切前端校验入库
            if (f[0].trim().isEmpty()) {
                errors.add("第" + (i + 1) + "行合同编号必填");
                continue;
            }
            if (f[1].trim().isEmpty()) {
                errors.add("第" + (i + 1) + "行合同名称必填");
                continue;
            }
            if (f[3].trim().isEmpty()) {
                errors.add("第" + (i + 1) + "行乙方（客商名称）必填");
                continue;
            }
            try {
                ContContract c = new ContContract();
                c.setTenantId(tenantId);
                c.setContractNo(f[0].trim());
                c.setContractName(f[1].trim());
                c.setContractType(f[2].trim());
                c.setPartyBName(f[3].trim());
                c.setAmount(new java.math.BigDecimal(f[4].trim()));
                // D-CON-1：签订公司ID留空 → 回填登录人公司，杜绝 company_id=NULL 导致列表不可见
                c.setCompanyId(f[5].trim().isEmpty() ? orgGuard.currentCompany() : Long.valueOf(f[5].trim()));
                if (c.getCompanyId() == null) {
                    errors.add("第" + (i + 1) + "行签订公司ID为空且无法从登录主体回填");
                    continue;
                }
                c.setItemId(f.length > 6 && !f[6].trim().isEmpty() ? Long.valueOf(f[6].trim()) : null);
                c.setBeginDate(f.length > 7 && !f[7].trim().isEmpty() ? LocalDate.parse(f[7].trim()) : null);
                c.setEndDate(f.length > 8 && !f[8].trim().isEmpty() ? LocalDate.parse(f[8].trim()) : null);
                c.setRemark(f.length > 9 ? f[9].trim() : null);
                c.setStatus("EFFECTIVE");
                c.setSource("IMPORT");
                // 合同编号唯一校验
                Long dup = contractMapper.selectCount(new LambdaQueryWrapper<ContContract>()
                        .eq(ContContract::getContractNo, c.getContractNo()));
                if (dup != null && dup > 0) {
                    errors.add("第" + (i + 1) + "行合同编号重复: " + c.getContractNo());
                    continue;
                }
                contractMapper.insert(c);
                ok++;
            } catch (Exception e) {
                // 单行导入失败不中断整批：错误按行收集后随响应返回（批量导入语义）
                errors.add("第" + (i + 1) + "行解析失败: " + e.getMessage());
            }
        }
        String msg = "成功导入 " + ok + " 条" + (errors.isEmpty() ? "" : "；失败: " + String.join("; ", errors));
        Result<Integer> r = Result.success(ok);
        r.setMsg(msg);
        return r;
    }
}
