// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.invoice.entity.InvInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 发票域：InvInvoice（带 tenant_id） */
@Mapper
public interface InvInvoiceMapper extends BaseMapper<InvInvoice> {

    /**
     * 占用计数漂移清单：一次 LEFT JOIN 出全部不一致行。
     *
     * <p>原先「取全租户发票 → 逐张 selectCount(inv_bill_invoice)」是 11.2 万次往返的 N+1，
     * 实测端点 57 秒；改成明细表按 invoice_id 一次分组后左连接，同一判定（used_count 与明细条数不等）
     * 只差在集合运算放在库里做。
     *
     * <p>子查询显式带 tenant_id：租户拦截器只改主表，不改原生 SQL 片段；
     * 明细侧带 del_flag=0，与 @TableLogic 的默认过滤一致（软删的勾稽行不计占用）。
     */
    @Select("SELECT i.id AS invoiceId, i.invoice_no AS invoiceNo, "
            + "COALESCE(i.used_count, 0) AS storedUsedCount, COALESCE(c.cnt, 0) AS actualUsedCount "
            + "FROM inv_invoice i "
            + "LEFT JOIN (SELECT invoice_id, COUNT(*) AS cnt FROM inv_bill_invoice "
            + "           WHERE tenant_id = #{tenantId} AND del_flag = 0 GROUP BY invoice_id) c "
            + "  ON c.invoice_id = i.id "
            + "WHERE i.tenant_id = #{tenantId} "
            + "  AND COALESCE(i.used_count, 0) <> COALESCE(c.cnt, 0) "
            + "ORDER BY i.id")
    List<Map<String, Object>> selectUsedCountDiff(@Param("tenantId") Long tenantId);
}
