package com.fssc.masterdata.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 在建工程转固定资产请求参数。
 * 转固时按累计归集金额建资产卡片，并携带折旧口径与目标资产归属。
 */
@Data
public class MdCipTransferRequest {

    /** 目标资产类别（md_asset_category.id，必填） */
    private Long categoryId;

    /** 固定资产使用部门（md_asset.deptId） */
    private Long deptId;

    /** 折旧方法：LINEAR 直线 / ACCELERATED 加速 */
    private String depreciationMethod;

    /** 折旧月数（会计口径，必填>0） */
    private Integer depreciationMonths;

    /** 税法折旧年限(月)，可空 */
    private Integer taxLifeMonths;

    /** 转固日期 */
    private LocalDate transferDate;

    /** 转固备注 */
    private String remark;
}