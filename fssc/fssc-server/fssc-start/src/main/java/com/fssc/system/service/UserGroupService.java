// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysGroupRole;
import com.fssc.system.entity.SysUserGroup;
import com.fssc.system.entity.SysUserGroupRel;
import com.fssc.system.mapper.SysGroupRoleMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserGroupMapper;
import com.fssc.system.mapper.SysUserGroupRelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 用户组服务（权限治理 v2）：用户组 = 批量授权单元，把一组角色批量授予一批用户。
 * <ul>
 *   <li>权限链路：用户 → 用户组（sys_user_group_rel）→ 组角色（sys_group_role）→ 菜单权限（sys_role_menu）。</li>
 *   <li>生效路径：登录 perms / 菜单树 / 角色码均已在 SysUserMapper / SysMenuMapper 做
 *       「直接角色 ∪ 用户组派生角色」UNION，无需额外装配。</li>
 *   <li>内控：组角色变更与成员变更都先跑用户级 SoD（权限码并集）再落库，命中即拦截。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final SysUserGroupMapper groupMapper;
    private final SysGroupRoleMapper groupRoleMapper;
    private final SysUserGroupRelMapper relMapper;
    private final SysRoleMapper roleMapper;
    private final SoDService sodService;

    public List<SysUserGroup> list() {
        return groupMapper.selectList(new LambdaQueryWrapper<SysUserGroup>()
                .orderByAsc(SysUserGroup::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(String groupCode, String groupName, String remark) {
        if (!StringUtils.hasText(groupCode) || !StringUtils.hasText(groupName)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户组编码与名称必填");
        }
        Long n = groupMapper.selectCount(new LambdaQueryWrapper<SysUserGroup>()
                .eq(SysUserGroup::getGroupCode, groupCode));
        if (n != null && n > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户组编码已存在: " + groupCode);
        }
        SysUserGroup g = new SysUserGroup();
        g.setTenantId(TenantContext.getTenantId());
        g.setGroupCode(groupCode.trim());
        g.setGroupName(groupName.trim());
        g.setStatus("NORMAL");
        g.setRemark(remark);
        LoginUser cu = UserContext.get();
        g.setCreateBy(cu == null ? "system" : cu.getUsername());
        groupMapper.insert(g);
        return g.getId();
    }

    /** 组角色替换：先对全部成员跑用户级 SoD（并集含新旧组角色，从严），通过后删旧插新。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long groupId, List<Long> roleIds) {
        requireGroup(groupId);
        if (roleIds != null && !roleIds.isEmpty()) {
            long exist = roleMapper.selectCount(new LambdaQueryWrapper<com.fssc.system.entity.SysRole>()
                    .in(com.fssc.system.entity.SysRole::getId, roleIds));
            if (exist < roleIds.stream().filter(java.util.Objects::nonNull).distinct().count()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在无效角色ID，请刷新后重试");
            }
        }
        // SoD：逐成员校验（直接角色 ∪ 旧组角色 ∪ 新组角色 的并集，从严合并）
        for (Long uid : memberIds(groupId)) {
            sodService.assertNoUserPermConflict(uid, roleIds);
        }
        groupRoleMapper.delete(new LambdaQueryWrapper<SysGroupRole>().eq(SysGroupRole::getGroupId, groupId));
        if (roleIds != null) {
            Long tenantId = TenantContext.getTenantId();
            for (Long roleId : roleIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
                SysGroupRole gr = new SysGroupRole();
                gr.setTenantId(tenantId);
                gr.setGroupId(groupId);
                gr.setRoleId(roleId);
                groupRoleMapper.insert(gr);
            }
        }
    }

    /** 组成员替换：先对每个新成员按组现有角色跑用户级 SoD，通过后删旧插新。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMembers(Long groupId, List<Long> userIds) {
        requireGroup(groupId);
        List<Long> groupRoleIds = roleIdsOf(groupId);
        if (userIds != null) {
            for (Long uid : userIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
                sodService.assertNoUserPermConflict(uid, groupRoleIds);
            }
        }
        relMapper.delete(new LambdaQueryWrapper<SysUserGroupRel>().eq(SysUserGroupRel::getGroupId, groupId));
        if (userIds != null) {
            Long tenantId = TenantContext.getTenantId();
            for (Long uid : userIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
                SysUserGroupRel rel = new SysUserGroupRel();
                rel.setTenantId(tenantId);
                rel.setUserId(uid);
                rel.setGroupId(groupId);
                relMapper.insert(rel);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long groupId) {
        requireGroup(groupId);
        relMapper.delete(new LambdaQueryWrapper<SysUserGroupRel>().eq(SysUserGroupRel::getGroupId, groupId));
        groupRoleMapper.delete(new LambdaQueryWrapper<SysGroupRole>().eq(SysGroupRole::getGroupId, groupId));
        groupMapper.deleteById(groupId);
    }

    public List<Long> roleIdsOf(Long groupId) {
        return groupRoleMapper.selectList(new LambdaQueryWrapper<SysGroupRole>()
                        .eq(SysGroupRole::getGroupId, groupId))
                .stream().map(SysGroupRole::getRoleId).toList();
    }

    public List<Long> memberIds(Long groupId) {
        return relMapper.selectList(new LambdaQueryWrapper<SysUserGroupRel>()
                        .eq(SysUserGroupRel::getGroupId, groupId))
                .stream().map(SysUserGroupRel::getUserId).toList();
    }

    private void requireGroup(Long groupId) {
        if (groupId == null || groupMapper.selectById(groupId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户组不存在: " + groupId);
        }
    }

    /** 防未使用告警 */
    @SuppressWarnings("unused")
    private static <T> List<T> safe(Collection<T> c) {
        return c == null ? new ArrayList<>() : new ArrayList<>(c);
    }
}
