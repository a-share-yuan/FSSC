// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.openapi.linku.service;

import com.fssc.openapi.linku.LinkuOpenGuard;
import com.fssc.openapi.linku.LinkuOpenProperties;
import com.fssc.openapi.linku.mapper.LinkuOpenQueryMapper;
import com.fssc.openapi.linku.mapper.LinkuOpenSyncMapper;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LinkU 开放接口查询服务。
 *
 * <p>把 BCP 侧的查询语义在 FSSC 内部收敛：BCP 只送「公司 + 伙伴码 + 期间 + 条数」，
 * 返回稳定 JSON（列别名即键），不暴露表结构。</p>
 *
 * <p>调用方（{@link LinkuOpenGuard}）已建立「忽略租户」上下文，
 * 本服务的全部 SQL 以入参 {@code tenantId} 显式过滤。</p>
 *
 * <p><b>代际优雅降级</b>：{@code ar_pre_invoice} 等蓝图代专有表在现役库不存在时，
 * 查询直接返回空列表（AR 三单匹配退化为两单匹配，不误判差异），与原直连实现同一规则。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkuOpenQueryService {

    private final LinkuOpenQueryMapper queryMapper;
    private final LinkuOpenSyncMapper syncMapper;
    private final LinkuOpenProperties props;

    public List<Map<String, Object>> payables(Long tenantId, Long companyId, String partnerCode,
                                             String period, Integer limit) {
        return queryMapper.selectPayables(tenantId, companyId, partnerCode, period, bound(limit));
    }

    public List<Map<String, Object>> payments(Long tenantId, Long companyId, String partnerCode, Integer limit) {
        int l = bound(limit);
        List<Map<String, Object>> paid = queryMapper.selectPayments(tenantId, companyId, partnerCode, l);
        if (!paid.isEmpty()) {
            return paid;
        }
        // 兜底：实付台账无数据时回退付款计划（与原直连适配器 规则一致）
        log.info("[LinkU开放接口] exp_bill_payment 无数据（company={}, partner={}），回退 pay_plan 付款计划腿",
                companyId, partnerCode);
        return queryMapper.selectPayPlanFallback(tenantId, companyId, partnerCode, l);
    }

    public List<Map<String, Object>> purchaseReceipts(Long tenantId, Long companyId, String partnerCode,
                                                      Integer limit) {
        return queryMapper.selectPurchaseReceipts(tenantId, companyId, partnerCode, bound(limit));
    }

    public List<Map<String, Object>> purchaseReturns(Long tenantId, Long companyId, String partnerCode,
                                                     String unifiedCode, Integer limit) {
        return queryMapper.selectPurchaseReturns(tenantId, companyId, partnerCode, unifiedCode, bound(limit));
    }

    public List<Map<String, Object>> receivables(Long tenantId, Long companyId, String partnerCode,
                                                 String period, Integer limit) {
        return queryMapper.selectReceivables(tenantId, companyId, partnerCode, period, bound(limit));
    }

    public List<Map<String, Object>> receipts(Long tenantId, Long companyId, String partnerCode, Integer limit) {
        return queryMapper.selectReceipts(tenantId, companyId, partnerCode, bound(limit));
    }

    /** AR 预制发票环节：表缺失（OTC 代）时返回空，与原直连实现同一规则。 */
    public List<Map<String, Object>> preInvoices(Long tenantId, Long companyId, String partnerCode,
                                                 Integer limit) {
        if (!tableAvailable("ar_pre_invoice")) {
            log.debug("[LinkU开放接口] ar_pre_invoice 不存在（OTC 代），AR 预制发票腿返回空");
            return List.of();
        }
        try {
            return queryMapper.selectPreInvoices(tenantId, companyId, partnerCode, bound(limit));
        } catch (DataAccessException e) {
            log.warn("[LinkU开放接口] ar_pre_invoice 查询失败，返回空 err={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * AR 销售订单环节（三单匹配改造的一腿）：候选表 {@code so_order}(+行) 缺失时返回空并告警。
     *
     * <p>与 {@link #preInvoices} 同一规则：表不存在不抛错、返回空列表 ——
     * 让 BCP 侧 AR 三单匹配判定为"缺订单环节 → 不自动通过"，<b>不回退采购订单</b>（回退即错配）。</p>
     */
    public List<Map<String, Object>> salesOrders(Long tenantId, Long companyId, String partnerCode,
                                                 String period, Integer limit) {
        if (!tableAvailable("so_order")) {
            log.warn("[LinkU开放接口] so_order 不存在：AR 订单腿数据源未就绪，返回空（不伪造数据）"
                    + " —— 需 ERP/销售模块提供销售订单物化表");
            return List.of();
        }
        try {
            return queryMapper.selectSalesOrderLines(tenantId, companyId, partnerCode, period, bound(limit));
        } catch (DataAccessException e) {
            log.warn("[LinkU开放接口] so_order 查询失败，返回空 err={}", e.getMessage());
            return List.of();
        }
    }

    /** AR 销售出库发货环节：候选表 {@code so_shipment} 缺失时返回空并告警。 */
    public List<Map<String, Object>> saleShipments(Long tenantId, Long companyId, String partnerCode,
                                                   Integer limit) {
        if (!tableAvailable("so_shipment")) {
            log.warn("[LinkU开放接口] so_shipment 不存在：AR 发货腿数据源未就绪，返回空（不伪造数据）"
                    + " —— 需 ERP/销售模块提供销售出库发货单表");
            return List.of();
        }
        try {
            return queryMapper.selectSaleShipments(tenantId, companyId, partnerCode, bound(limit));
        } catch (DataAccessException e) {
            log.warn("[LinkU开放接口] so_shipment 查询失败，返回空 err={}", e.getMessage());
            return List.of();
        }
    }

    /** 往来单位主数据：供应商 + 客户（按 code 精确或按 limit 列举）。 */
    public Map<String, Object> partner(Long tenantId, String partnerCode, String type, Integer limit) {
        int l = bound(limit);
        Map<String, Object> out = new HashMap<>();
        String t = type == null ? null : type.trim().toUpperCase();
        if (t == null || "SUPPLIER".equals(t) || "ALL".equals(t)) {
            out.put("suppliers", queryMapper.selectSuppliers(tenantId, partnerCode, l));
        }
        if (t == null || "CUSTOMER".equals(t) || "ALL".equals(t)) {
            out.put("customers", queryMapper.selectCustomers(tenantId, partnerCode, l));
        }
        return out;
    }

    /**
     * 期间冻结状态：{@code gl_month_close} 该期间最新一条记录 {@code status='CLOSED'} 即视为冻结
     * （与 {@code bcp.adapter.fssc.otc-freeze-on-month-close} 规则一致；规则比蓝图代更严格）。
     */
    public Map<String, Object> periodStatus(Long tenantId, Long companyId, String period) {
        Map<String, Object> out = new HashMap<>();
        out.put("period", period);
        List<Map<String, Object>> rows = queryMapper.selectPeriodStatus(tenantId, companyId, period);
        String status = rows.isEmpty() || rows.get(0).get("status") == null
                ? null : String.valueOf(rows.get(0).get("status"));
        out.put("monthCloseStatus", status);
        out.put("frozen", "CLOSED".equals(status));
        return out;
    }

    /** 表存在性缓存（首次探测后按表名缓存，避免每次查询都打 information_schema）。 */
    private final Map<String, Boolean> tableCache = new ConcurrentHashMap<>();

    private boolean tableAvailable(String table) {
        Boolean cached = tableCache.get(table);
        if (cached != null) {
            return cached;
        }
        boolean exists;
        try {
            exists = syncMapper.tableExists(table) > 0;
        } catch (DataAccessException e) {
            log.warn("[LinkU开放接口] 表存在性探测失败 table={} err={}", table, e.getMessage());
            exists = true; // 探测失败按存在处理，让查询按真实错误暴露，不静默吞掉
        }
        tableCache.put(table, exists);
        return exists;
    }

    private int bound(Integer limit) {
        int l = limit == null ? 100 : limit;
        if (l <= 0) {
            l = 100;
        }
        return Math.min(l, props.maxPageSize());
    }
}
