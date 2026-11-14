package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 条码管理（op_barcode）。
 * 支持单据条码、档案条码、档案盒条码三类。
 * 生命周期：GENERATED -> PRINTED -> SCANNED -> SHELVED
 */
@Data
@TableName("op_barcode")
public class OpBarcode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 条码值（一维码/二维码内容） */
    private String barcode;
    /** 条码类型: CODE128/QRCODE */
    private String barcodeType;
    /** 业务类型: BILL单据/ARCHIVE档案/BOX档案盒 */
    private String bizType;
    /** 关联业务ID */
    private Long refId;
    /** 关联业务编号 */
    private String refNo;
    /** 打印人 */
    private Long printBy;
    private String printName;
    private LocalDateTime printTime;
    /** 扫描人 */
    private Long scanBy;
    private String scanName;
    private LocalDateTime scanTime;
    /** 状态: GENERATED/PRINTED/SCANNED/SHELVED */
    private String status;
    /** 上架位置 */
    private String location;
    private LocalDateTime shelveTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
