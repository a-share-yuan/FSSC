// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.openapi.linku.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * LinkU 开放接口审计/去重日志 Mapper（表 {@code linku_sync_log}）。
 *
 * <p>与既有回调复用同一张表。此处刻意<b>不使用</b> MP 实体（{@code LinkuSyncLog}），
 * 而是显式 SQL：开放接口需要写入可选的 {@code idempotency_key} 列，
 * 而该列是不久前新增的，在未执行 DDL 的库里不存在 —— 显式 SQL 才能在列缺失时降级
 * （见 {@link #insertWithoutKey}），实体方式会因列固定而整体写入失败。</p>
 *
 * <p>{@code linku_sync_log} 无 {@code tenant_id} 列，已在
 * {@code FsscTenantLineHandler} 全局表白名单登记，故不受租户插件追加条件影响。</p>
 */
@Mapper
public interface LinkuOpenLogMapper {

    /**
     * 去重判定：同事件类型 + 同唯一键已成功处理过。
     * <p>仅在 {@code idempotency_key} 列存在时可用；列缺失由服务层捕获后降级。</p>
     */
    @Select("SELECT COUNT(1) FROM linku_sync_log WHERE event_type = #{eventType} "
            + "AND idempotency_key = #{idempotencyKey} AND response_status = 'SUCCESS'")
    int countSuccessByIdempotencyKey(@Param("eventType") String eventType,
                                     @Param("idempotencyKey") String idempotencyKey);

    /** 含唯一键的审计写入数据库（列存在时）。 */
    @Insert("INSERT INTO linku_sync_log (event_type, linku_recon_id, supplier_id, request_body, "
            + "response_status, error_msg, duration_ms, idempotency_key, create_time) "
            + "VALUES (#{eventType}, #{linkuReconId}, #{supplierId}, #{requestBody}, "
            + "#{responseStatus}, #{errorMsg}, #{durationMs}, #{idempotencyKey}, #{createTime})")
    int insertWithKey(@Param("eventType") String eventType,
                      @Param("linkuReconId") Long linkuReconId,
                      @Param("supplierId") Long supplierId,
                      @Param("requestBody") String requestBody,
                      @Param("responseStatus") String responseStatus,
                      @Param("errorMsg") String errorMsg,
                      @Param("durationMs") Integer durationMs,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("createTime") LocalDateTime createTime);

    /** 不含唯一键的审计写入数据库（列缺失时的降级路径）。 */
    @Insert("INSERT INTO linku_sync_log (event_type, linku_recon_id, supplier_id, request_body, "
            + "response_status, error_msg, duration_ms, create_time) "
            + "VALUES (#{eventType}, #{linkuReconId}, #{supplierId}, #{requestBody}, "
            + "#{responseStatus}, #{errorMsg}, #{durationMs}, #{createTime})")
    int insertWithoutKey(@Param("eventType") String eventType,
                         @Param("linkuReconId") Long linkuReconId,
                         @Param("supplierId") Long supplierId,
                         @Param("requestBody") String requestBody,
                         @Param("responseStatus") String responseStatus,
                         @Param("errorMsg") String errorMsg,
                         @Param("durationMs") Integer durationMs,
                         @Param("createTime") LocalDateTime createTime);
}
