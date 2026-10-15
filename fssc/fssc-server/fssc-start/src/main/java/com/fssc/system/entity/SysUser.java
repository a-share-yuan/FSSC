package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fssc.common.entity.TenantAuditBase;
import com.fssc.framework.mask.Sensitive;
import com.fssc.framework.mask.SensitiveType;

/**
 * 租户用户（sys_user，带 tenant_id）。
 * 用于真实登录与后续租户系统域。密码 BCrypt 存储且 @JsonIgnore 不外泄。
 */
@TableName("sys_user")
public class SysUser extends TenantAuditBase {

    /** 登录账号（租户内唯一） */
    @TableField("user_name")
    private String userName;

    /** 姓名 */
    @TableField("nick_name")
    private String nickName;

    private String empNo;

    /** BCrypt 密码，序列化不输出 */
    @JsonIgnore
    private String password;

    private Long companyId;
    private Long deptId;
    private Long postId;
    private String phone;
    private String email;
    private String sex;
    /** NORMAL正常 / DISABLED停用 / LOCKED锁定 */
    private String status;
    /** 1 租户管理员（租户内最高权限） */
    private Integer isAdmin;

    /** 部门名称（仅出参展示用，非库表列，page 时填充） */
    @TableField(exist = false)
    private String deptName;

    /** 公司名称（仅出参展示用，非库表列，page 时填充） */
    @TableField(exist = false)
    private String companyName;

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    /** P1-3：手机号出参脱敏（138****8000） */
    @Sensitive(SensitiveType.PHONE)
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    /** P1-3：邮箱出参脱敏（ab***@x.com） */
    @Sensitive(SensitiveType.EMAIL)
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Integer isAdmin) { this.isAdmin = isAdmin; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
}
