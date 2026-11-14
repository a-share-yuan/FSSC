package com.fssc.image.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 影像系统设置：业务类型设置 / 文件种类识别设置。
 * setting_type: BIZ_TYPE 业务类型 / FILE_KIND 文件种类识别。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("img_setting")
public class ImgSetting extends TenantAuditBase {
    private static final long serialVersionUID = 1L;

    /** BIZ_TYPE 业务类型 / FILE_KIND 文件种类识别 */
    private String settingType;
    /** 设置编码 */
    private String settingCode;
    /** 设置名称 */
    private String settingName;
    /** 扩展 JSON（识别规则/关联配置） */
    private String extInfo;
    /** 状态 NORMAL/DISABLED */
    private String status;
    /** 备注 */
    private String remark;
}
