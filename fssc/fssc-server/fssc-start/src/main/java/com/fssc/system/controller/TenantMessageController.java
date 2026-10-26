// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysMessage;
import com.fssc.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内消息/待办提醒（/api/tenant/system/message/**）。 */
@RestController
@RequestMapping("/api/tenant/system/message")
@RequiredArgsConstructor
public class TenantMessageController {

    private final MessageService messageService;

    /** 发送消息（业务链路调用：审批完成、单据退回、预警等场景） */
    @RequiresPerm("system:message:send")
    @OperLog(module = "SYSTEM_MESSAGE", operType = "INSERT", desc = "发送消息")
    @PostMapping("/send")
    public Result<Long> send(@RequestBody SendReq req) {
        // 必填办结（批次 2C，与前端 GenericList「发送站内消息」弹窗同一规则）。
        // 拦在控制器而非 MessageService.send：内部链路 NotifyService 也走同一方法，其模板允许无正文。
        AssertUtil.notNull(req.getReceiverId(), ErrorCode.PARAM_INVALID, "请选择接收人");
        AssertUtil.notBlank(req.getTitle(), ErrorCode.PARAM_INVALID, "请输入标题");
        AssertUtil.notBlank(req.getContent(), ErrorCode.PARAM_INVALID, "请输入消息内容");
        return Result.success(messageService.send(req.getReceiverId(), req.getMsgType(),
                req.getTitle(), req.getContent(), req.getBizType(), req.getBizId(), req.getLinkUrl()));
    }

    /**
     * 消息分页（D-DEC-149 通知出口可达性办结）。
     *
     * <p>原规则：无论查谁都要求 {@code system:message:list}。收件人读<b>自己的</b>站内信是个人视角，
     * 与 {@code /wf/query/cc/mine} 同型——该码在授权表里并非人人有（现网 COMMON_EMP 持，
     * 但 FSSC_SUPERVISOR 单岗用户不持），一旦抄送提醒发给了一个不持该码的岗位，
     * 前端 {@code silent:true} 把 403 吞成「暂无通知」＝写了 sys_message 却仍然没有出口。
     * 现改为：<b>查自己不设权限码</b>，查他人仍需 {@code system:message:list}（管理视图不扩面）。
     */
    @GetMapping("/page")
    public Result<PageResult<SysMessage>> page(PageQuery query,
                                               @RequestParam(required = false) Long receiverId,
                                               @RequestParam(required = false) String msgType,
                                               @RequestParam(required = false) Integer isRead) {
        com.fssc.framework.security.LoginUser me = com.fssc.framework.security.UserContext.getRequired();
        boolean self = receiverId != null && receiverId.equals(me.getUserId());
        if (!self) {
            assertPerm(me, "system:message:list");
        }
        return Result.success(messageService.page(query.getPageNum(), query.getPageSize(),
                receiverId, msgType, isRead));
    }

    /** 未读数：本人视角免权限码（铃铛徽标），查他人仍需 {@code system:message:list}。规则同 {@link #page} */
    @GetMapping("/unreadCount")
    public Result<Long> unreadCount(@RequestParam Long receiverId) {
        com.fssc.framework.security.LoginUser me = com.fssc.framework.security.UserContext.getRequired();
        if (!receiverId.equals(me.getUserId())) {
            assertPerm(me, "system:message:list");
        }
        return Result.success(messageService.unreadCount(receiverId));
    }

    /**
     * 标记已读（D-DEC-149/187 已读回写链路）。
     *
     * <p>原规则只认 {@code system:message:update}，而 FIND_IN_SET 反查授权表＝该码<b>仅 IT_ADMIN 一岗</b>
     * （sys_menu id=3319）。业务岗位读不到也清不掉 ⇒ 抄送/待办提醒发出后永久停在未读态，
     * 徽标按整页计数越积越多，"有出口"退化成"一屏过期噪声"（前端 {@code catch{}} 还会把 403 吞掉，
     * 用户连失败都看不见）。现改为：<b>收件人本人免权限码</b>，代他人标记仍需该码。
     * 归属校验必须留着——{@code sys_message.id} 是自增主键、可枚举。
     */
    @OperLog(module = "SYSTEM_MESSAGE", operType = "UPDATE", desc = "消息已读")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        com.fssc.framework.security.LoginUser me = com.fssc.framework.security.UserContext.getRequired();
        SysMessage msg = messageService.find(id);
        if (msg == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在: " + id);
        }
        if (!me.getUserId().equals(msg.getReceiverId())) {
            assertPerm(me, "system:message:update");
        }
        messageService.markRead(id);
        return Result.success();
    }

    /** 全部标记已读：本人视角免权限码，代他人清空仍需 {@code system:message:update}。规则同 {@link #markRead} */
    @OperLog(module = "SYSTEM_MESSAGE", operType = "UPDATE", desc = "全部已读")
    @PostMapping("/readAll")
    public Result<Void> markAllRead(@RequestParam Long receiverId) {
        com.fssc.framework.security.LoginUser me = com.fssc.framework.security.UserContext.getRequired();
        if (!receiverId.equals(me.getUserId())) {
            assertPerm(me, "system:message:update");
        }
        messageService.markAllRead(receiverId);
        return Result.success();
    }

    @RequiresPerm("system:message:delete")
    @OperLog(module = "SYSTEM_MESSAGE", operType = "DELETE", desc = "删除消息")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return Result.success();
    }

    /**
     * 编程式权限判定（D-DEC-149/187）：这些端点按「本人视角免码、涉他人视角要码」分叉，
     * 注解 {@code @RequiresPerm} 是方法级一刀切、表达不了这个条件，故就地复刻切面的语义——
     * 通配 {@code *:*:*}（平台超管/租户管理员）视同持有，与 {@code PermissionAspect} 逐字一致，
     * 免得改了反而把管理员挡在外面。
     */
    private void assertPerm(com.fssc.framework.security.LoginUser me, String code) {
        java.util.Set<String> perms = me == null ? null : me.getPerms();
        if (perms != null && (perms.contains("*:*:*") || perms.contains(code))) {
            return;
        }
        throw new BizException(ErrorCode.PERMISSION_DENIED, "缺少权限：" + code);
    }

    /** 发送消息请求体 */
    public static class SendReq {
        private Long receiverId;
        private String msgType;
        private String title;
        private String content;
        private String bizType;
        private Long bizId;
        private String linkUrl;

        public Long getReceiverId() { return receiverId; }
        public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
        public String getMsgType() { return msgType; }
        public void setMsgType(String msgType) { this.msgType = msgType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getBizType() { return bizType; }
        public void setBizType(String bizType) { this.bizType = bizType; }
        public Long getBizId() { return bizId; }
        public void setBizId(Long bizId) { this.bizId = bizId; }
        public String getLinkUrl() { return linkUrl; }
        public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    }
}
