package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 发票邮包（蓝图 SCC_AR_07）：创建→寄送→邮件通知→签收→归档。 */
@Data
@TableName("ar_mail_package")
public class ArMailPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String packageNo;
    private Long companyId;
    private String customerCode;
    private String customerName;
    private String receiver;
    private String address;
    private String courierCompany;
    private String courierNo;
    private String invoiceNosJson;
    private Integer mailNotified;
    /** CREATED/SENT/SIGNED/ARCHIVED */
    private String status;
    private LocalDateTime signTime;
    private LocalDateTime archiveTime;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_CREATED = "CREATED";
    public static final String ST_SENT = "SENT";
    public static final String ST_SIGNED = "SIGNED";
    public static final String ST_ARCHIVED = "ARCHIVED";
}
