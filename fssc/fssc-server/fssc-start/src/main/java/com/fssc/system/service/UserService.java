// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.MaskUtil;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.mapper.SysCompanyMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 租户用户管理：CRUD + 停用/启用 + 重置密码 + 分配角色 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final PasswordEncoder passwordEncoder;
    private final SoDService sodService;

public PageResult<SysUser> page(long pageNum, long pageSize, String keyword, String status) {
        return page(pageNum, pageSize, keyword, status, null, null);
    }

    /**
     * 用户分页（2026-09-17 优化：支持机构目录树联动过滤）。
     * @param deptIds   部门 ID 集合（选中部门节点 = 该部门及其全部子部门），null/空 不过滤
     * @param companyId 公司 ID（选中公司节点），null 不过滤
     */
    public PageResult<SysUser> page(long pageNum, long pageSize, String keyword, String status,
                                    List<Long> deptIds, Long companyId) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        // P0-1 租户数据隔离：仅返回当前租户用户，防止跨租户演示账号出现在人员下拉/选择器
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            qw.eq(SysUser::getTenantId, tenantId);
        }
        // @TableLogic 已自动追加 del_flag=0
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUserName, keyword)
                    .or().like(SysUser::getNickName, keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(SysUser::getStatus, status);
        }
        // 机构目录树联动过滤：部门（含子部门集合）或公司
        if (deptIds != null && !deptIds.isEmpty()) {
            qw.in(SysUser::getDeptId, deptIds);
        }
        if (companyId != null && companyId > 0) {
            qw.eq(SysUser::getCompanyId, companyId);
        }
        qw.orderByAsc(SysUser::getId);
        Page<SysUser> p = sysUserMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // 部门/公司名称按本次页内 ID 批量查（改前逐行 selectById：200 人 = 400 次往返，
        // 人员下拉首屏实测 1.5~1.7s）
        java.util.Set<Long> deptIdsOfPage = new java.util.HashSet<>();
        java.util.Set<Long> companyIdsOfPage = new java.util.HashSet<>();
        for (SysUser u : p.getRecords()) {
            if (u.getDeptId() != null && u.getDeptId() > 0) deptIdsOfPage.add(u.getDeptId());
            if (u.getCompanyId() != null && u.getCompanyId() > 0) companyIdsOfPage.add(u.getCompanyId());
        }
        Map<Long, String> deptNames = new java.util.HashMap<>();
        if (!deptIdsOfPage.isEmpty()) {
            for (SysDept d : sysDeptMapper.selectByIds(deptIdsOfPage)) {
                deptNames.put(d.getId(), d.getDeptName());
            }
        }
        Map<Long, String> companyNames = new java.util.HashMap<>();
        if (!companyIdsOfPage.isEmpty()) {
            for (SysCompany c : sysCompanyMapper.selectByIds(companyIdsOfPage)) {
                companyNames.put(c.getId(), c.getCompanyName());
            }
        }
        // P1-3：列表出参对联系方式脱敏（HTTP 出参由 Boot4 的 Jackson3 序列化，
        // 不经过 Jackson2 的 @Sensitive 模块，故在此显式脱敏；明文走 /{id}/full 授权接口）
        for (SysUser u : p.getRecords()) {
            u.setPhone(MaskUtil.phone(u.getPhone()));
            u.setEmail(MaskUtil.email(u.getEmail()));
            // 填充部门名称、公司名称（前端报销页/申请页下拉时需要显示申请人部门公司）
            if (u.getDeptId() != null && u.getDeptId() > 0) {
                u.setDeptName(deptNames.get(u.getDeptId()));
            }
            if (u.getCompanyId() != null && u.getCompanyId() > 0) {
                u.setCompanyName(companyNames.get(u.getCompanyId()));
            }
        }
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 新增用户（默认密码 123456，可传入 roleIds 一并分配角色） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUser user, List<Long> roleIds) {
        long dup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserName, user.getUserName()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "登录账号已存在: " + user.getUserName());
        }
        // 手机号租户内唯一校验
        if (StringUtils.hasText(user.getPhone())) {
            long phoneDup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, user.getPhone()));
            if (phoneDup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被占用: " + user.getPhone());
            }
        }
        user.setId(null);
        user.setTenantId(TenantContext.getTenantId()); // 显式设置，不依赖 MetaObjectHandler
        user.setPassword(passwordEncoder.encode(StringUtils.hasText(user.getPassword()) ? user.getPassword() : "123456"));
        if (user.getStatus() == null) {
            user.setStatus("NORMAL");
        }
        sysUserMapper.insert(user);
        assignRoles(user.getId(), roleIds);
        return user.getId();
    }

    public void update(SysUser user) {
        if (sysUserMapper.selectById(user.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户不存在: " + user.getId());
        }
        // 手机号租户内唯一校验（排除自身）
        if (StringUtils.hasText(user.getPhone())) {
            long phoneDup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, user.getPhone())
                    .ne(SysUser::getId, user.getId()));
            if (phoneDup > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被占用: " + user.getPhone());
            }
        }
        // 密码不允许走 update（必须走重置密码接口）
        user.setPassword(null);
        sysUserMapper.updateById(user);
    }

    public void enable(Long id, boolean enable) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        sysUserMapper.updateById(u);
    }

    public void resetPassword(Long id, String newPassword) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户不存在: " + id);
        }
        String pwd = StringUtils.hasText(newPassword) ? newPassword : "123456";
        SysUser u = new SysUser();
        u.setId(id);
        u.setPassword(passwordEncoder.encode(pwd));
        sysUserMapper.updateById(u);
    }

    /** 分配角色：删旧插新（同租户内）。授权前置双重 SoD：角色码两两互斥 + 用户级权限码并集校验。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        // SoD 内控①：合并本次新角色与用户已有角色逐对检查互斥表（角色码级），命中抛异常拦截授权
        sodService.assertNoConflict(userId, roleIds);
        // SoD 内控②：用户级权限码并集校验（含用户组派生角色），捕获"角色各自合法、组合违规"
        sodService.assertNoUserPermConflict(userId, roleIds);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            Long tenantId = TenantContext.getTenantId();
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setTenantId(tenantId); // 显式设置，不依赖 MetaObjectHandler
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    public List<Long> roleIds(Long userId) {
        return sysUserRoleMapper.selectRoleIdsByUser(userId);
    }

    /**
     * P1-3：授权查看用户明文联系方式。
     *
     * <p>列表接口对 phone/email 已脱敏（{@code @Sensitive}），编辑表单需要可回填的明文，
     * 故单独提供本方法；返回 Map 而非 SysUser，避免命中脱敏序列化器。
     * 调用方须具备 system:user:update 权限。</p>
     */
    public Map<String, Object> fullContact(Long userId) {
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户不存在: " + userId);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        return m;
    }

    /** 删除用户（逻辑删；同时清理用户-角色关联，关联表物理删） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        sysUserMapper.hardDelete(userId);
    }

    /**
     * 批量导入用户（Excel）。
     * 期望列顺序：用户名, 姓名, 手机号, 邮箱, 初始密码(可选)
     * 从第 2 行开始读取，跳过表头。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchImportResult batchImport(MultipartFile file) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        Long tenantId = TenantContext.getTenantId();
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String userName = getCellStr(row, 0);
                    String nickName = getCellStr(row, 1);
                    String phone = getCellStr(row, 2);
                    String email = getCellStr(row, 3);
                    String password = getCellStr(row, 4);
                    if (!StringUtils.hasText(userName)) {
                        failures.add("第" + (i + 1) + "行：用户名为空");
                        continue;
                    }
                    // 检查用户名唯一性
                    long dup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUserName, userName));
                    if (dup > 0) {
                        failures.add("第" + (i + 1) + "行：用户名已存在 " + userName);
                        continue;
                    }
                    // 检查手机号唯一性
                    if (StringUtils.hasText(phone)) {
                        long phoneDup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getPhone, phone));
                        if (phoneDup > 0) {
                            failures.add("第" + (i + 1) + "行：手机号已被占用 " + phone);
                            continue;
                        }
                    }
                    SysUser user = new SysUser();
                    user.setTenantId(tenantId);
                    user.setUserName(userName);
                    user.setNickName(StringUtils.hasText(nickName) ? nickName : userName);
                    user.setPhone(phone);
                    user.setEmail(email);
                    user.setPassword(passwordEncoder.encode(StringUtils.hasText(password) ? password : "123456"));
                    user.setStatus("NORMAL");
                    sysUserMapper.insert(user);
                    successes.add(userName);
                } catch (Exception e) {
                    // 单行导入失败不中断整批：错误按行收集后随 BatchImportResult 返回
                    failures.add("第" + (i + 1) + "行：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Excel 解析失败: " + e.getMessage());
        }
        BatchImportResult result = new BatchImportResult();
        result.setSuccessCount(successes.size());
        result.setFailCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    private String getCellStr(Row row, int idx) {
        var cell = row.getCell(idx);
        return cell == null ? "" : cell.toString().trim();
    }

    /** 批量导入结果 */
    public static class BatchImportResult {
        private int successCount;
        private int failCount;
        private List<String> failures;

        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public List<String> getFailures() { return failures; }
        public void setFailures(List<String> failures) { this.failures = failures; }
    }
}
