package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;

import java.time.LocalDateTime;

/**
 * 租户主表（pf_tenant）。状态机：
 * APPLYING待审核 → NORMAL正常 → FROZEN冻结 → CANCELLING注销中 → CANCELLED已注销；
 * APPLYING → REJECTED已拒绝；FROZEN → NORMAL（解冻）。
 */
@TableName("pf_tenant")
public class PfTenant extends PlatformBaseEntity {

    private String tenantCode;
    private String tenantName;
    private String shortName;
    private String creditCode;
    private String industry;
    private String scale;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String domain;
    private String logo;
    private String sysTitle;
    /** APPLYING/NORMAL/FROZEN/CANCELLING/CANCELLED/REJECTED */
    private String status;
    private String freezeReason;
    private String rejectReason;
    private LocalDateTime effectTime;
    private LocalDateTime expireTime;
    /** 租户超管用户ID（指向 sys_user） */
    private Long adminUserId;
    private String remark;

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getSysTitle() { return sysTitle; }
    public void setSysTitle(String sysTitle) { this.sysTitle = sysTitle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFreezeReason() { return freezeReason; }
    public void setFreezeReason(String freezeReason) { this.freezeReason = freezeReason; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public LocalDateTime getEffectTime() { return effectTime; }
    public void setEffectTime(LocalDateTime effectTime) { this.effectTime = effectTime; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
