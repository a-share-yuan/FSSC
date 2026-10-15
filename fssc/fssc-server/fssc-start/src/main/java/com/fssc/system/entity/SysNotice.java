package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知公告（sys_notice，带 tenant_id）。
 * 状态：DRAFT草稿/PUBLISHED已发布/CLOSED已关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends TenantAuditBase {

    private String noticeTitle;
    /** NOTICE通知/ANNOUNCE公告 */
    private String noticeType;
    /** 公告内容（富文本） */
    private String noticeContent;
    /** 发布时间 */
    private java.time.LocalDateTime publishTime;
    /** DRAFT/PUBLISHED/CLOSED */
    private String status;
    private String remark;
}
