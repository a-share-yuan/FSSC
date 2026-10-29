// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.List;

/**
 * ④ 数据防线：MyBatis-Plus 租户拦截处理器。
 * <p>
 * 所有业务表 SQL 在执行前会被自动追加 {@code WHERE tenant_id = ?}，
 * 调用方完全无感，从根上杜绝"忘了写 tenant_id 条件"导致的串户。
 * <p>
 * 不追加的场景（ignoreTable 返回 true）：
 * <ul>
 *   <li>全局 ignore：{@link TenantContext#runIgnoreTenant} 显式开启（跨租户聚合专用）。</li>
 *   <li>平台运营表 pf_*：平台端专属，无 tenant_id。</li>
 *   <li>全局共享定义表 sys_menu / sys_dict_type_def / sys_dict_data_def。</li>
 *   <li>跨租户全局查重索引表 inv_invoice_global：按发票代码+号码全局唯一，跨企业比对，无 tenant_id。</li>
 *   <li>流程引擎表 ACT_* / FLW_*：Flowable 自管，走其原生 TENANT_ID_ 字段。</li>
 * </ul>
 * 注意：wf_* 业务视图表带 tenant_id，【不】在忽略名单内，必须被过滤。
 */
@Component
public class FsscTenantLineHandler implements TenantLineHandler {

    /**
     * 全局共享定义表（一套菜单/内置字典，全平台共用）+ 跨租户全局查重索引表
     * + 运维/内控全局表：
     *   - op_job_heartbeat：定时任务心跳，跨租户全局运维表，无 tenant_id，且由调度线程（无租户上下文）写入。
     *   - sod_role_conflict：SoD 互斥规则，tenant_id NULL=平台级规则需跨租户读取，故不自动追加过滤，
     *     由 SoDService 手工按 (tenant_id IS NULL OR tenant_id = 当前租户) 取规则。
     *   - linku_sync_log（补登记）：LinkU 供应商回函 webhook 的同步审计日志。回函由外部系统
     *     **匿名**调用（见 JwtAuthFilter 白名单 linku-* 路径），此时根本没有租户上下文；而该表本就没有
     *     tenant_id 列。若不在白名单，任何经 MP 的查询都会被追加 tenant_id 条件 —— 轻则 SQL 报
     *     "Unknown column 'tenant_id'"，重则 TenantContext.getTenantId() fail-closed 直接抛
     *     "租户上下文缺失"。链路上的租户归属靠 linku_recon_id 关联业务单据推导，不需要列级隔离。
     *   - sys_schema_version（补登记）：数据库迁移版本台账，库级元数据，
     *     由运维脚本在无租户上下文中读写，不属于任何租户。
     *
     * 【登记规则】凡"无 tenant_id 列"的表，必须在此显式登记并写明理由；
     *  被排除在外的表由 scripts/health/check_tenant_coverage.sql 巡检暴露，默认按"应隔离"处理。
     */
    private static final Set<String> GLOBAL_DEF_TABLES = Set.of(
            "sys_menu", "sys_dict_type_def", "sys_dict_data_def", "inv_invoice_global",
            "op_job_heartbeat", "sod_role_conflict", "sod_perm_conflict",
            "linku_sync_log", "sys_schema_version");

    @Override
    public Expression getTenantId() {
        // TenantContext.getTenantId() 内部已 fail-closed：无租户条件即抛异常
        Long tid = TenantContext.getTenantId();
        return new LongValue(tid);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        String t = tableName.toLowerCase();
        if (TenantContext.isIgnoreTenant()) {
            return true;
        }
        if (t.startsWith("pf_")) {
            return true;
        }
        if (GLOBAL_DEF_TABLES.contains(t)) {
            return true;
        }
        if (t.startsWith("act_") || t.startsWith("flw_")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean ignoreInsert(List<net.sf.jsqlparser.schema.Column> columns, String tenantIdLine) {
        // fail-closed 改写（恒 true 时，拦截器在 INSERT 面既不注入也不校验）：
        // 1) 列清单已含 tenant_id（MetaObjectHandler 已按上下文填充，或业务代码显式赋值）：
        //    按 MP 默认语义跳过注入避免列重复；值面校验由 FsscMetaObjectHandler.fillTenant 把关
        //    （入参携带却与上下文不一致即抛错，不静默改写）。
        // 2) 列清单不含 tenant_id 且处于真实租户上下文：交给拦截器按上下文补注入，
        //    杜绝「实体缺列/值为空」时租户归属静默丢失；无上下文、平台端与聚合模式维持旧行为
        //    （该区间的无列写入由 scripts/health/check_tenant_coverage.sql 巡检暴露）。
        if (columns != null && columns.stream()
                .anyMatch(c -> tenantIdLine.equalsIgnoreCase(c.getColumnName()))) {
            return true;
        }
        return !(TenantContext.hasContext() && !TenantContext.isPlatform() && !TenantContext.isIgnoreTenant());
    }
}
