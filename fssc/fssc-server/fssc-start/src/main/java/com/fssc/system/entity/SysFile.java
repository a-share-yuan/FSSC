package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件（sys_file，带 tenant_id，物理+逻辑隔离双保险）。
 * 注意：sys_* 表 create_by/update_by 在 DDL 中为 bigint，故继承 TenantAuditBase（Long 审计），
 * 不能继承 BaseEntity（String 审计会触发 MySQL 1366 Incorrect integer value）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends TenantAuditBase {

    private String fileName;
    /** 相对存储路径（含租户目录） */
    private String filePath;
    /** 扩展名（小写） */
    private String fileSuffix;
    /** 文件大小（字节） */
    private Long fileSize;
    /** MIME 类型 */
    private String contentType;
    /** 文件 MD5（租户内秒传去重） */
    private String fileMd5;
    /** LOCAL/MINIO/OSS/COS */
    private String storageType;
    /** 存储区域 TEMP临时附件/BUSINESS业务正式影像/ARCHIVE归档档案（三区逻辑隔离） */
    private String storageZone;
    /** AVATAR/INVOICE/BILL_ATTACH/CONTRACT/IMPORT 等 */
    private String bizType;
    /** 关联业务主键 */
    private Long bizId;
    /** 上传人用户ID */
    private Long uploadBy;
}
