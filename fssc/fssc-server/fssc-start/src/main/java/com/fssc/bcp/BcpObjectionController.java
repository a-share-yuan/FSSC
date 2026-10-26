// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bcp;

import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 「BCP 异议审核」（{@code /api/tenant/bcp-objection/**}）—— 共享中心 AP/AR 岗对
 * BCP 门户伙伴提交的<b>对账明细行级异议</b>做审核。
 *
 * <h3>业务链路</h3>
 * 供应商（SCP/AP）或客户（CCP/AR）在 BCP 门户逐行对数量、单价提起异议并填写请求值；
 * 本页的 AP/AR 人员审核：<b>同意</b>即以伙伴提交的数量/单价替换 BCP 对账明细行的对应值，
 * <b>驳回</b>则明细行一字不动。
 *
 * <h3>它不改 FSSC 的账</h3>
 * 本轮规则（Q3=A）只改 BCP 的对账规则，<b>不回写 FSSC 业务单据</b>：
 * 应付/应收/入库/收付款单的权威值仍归财务系统，异议只在对账记录里体现"从多少改成了多少"。
 *
 * <h3>查询与回写都走 BCP 开放通道</h3>
 * FSSC 单数据源读不到 {@code bcp_*} 表，本类的每个方法都经 {@link BcpOpenClient}
 * 以 HMAC-SHA256 签名调用 BCP 门户的 {@code /api/open/linku/**}；未配置共享密钥时 fail-closed。
 */
@RestController
@RequestMapping("/api/tenant/bcp-objection")
@RequiredArgsConstructor
public class BcpObjectionController {

    private final BcpOpenClient bcpOpenClient;

    /**
     * 待审异议清单。
     *
     * @param direction 可选 AP（供应商侧）/ AR（客户侧）过滤；不传=两侧全查
     * @param limit     条数上限（BCP 侧钳位 1..500）
     */
    @RequiresPerm("bcp:objection:list")
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending(@RequestParam(required = false) String direction,
                                                     @RequestParam(required = false) Integer limit) {
        return Result.success(bcpOpenClient.listPending(direction, limit));
    }

    /**
     * 审核结论。
     *
     * @param id   异议 id（{@code bcp_recon_objection.id}）
     * @param body {@code {"approve":true,"remark":"..."}} —— approve 必填，
     *             显式区分"同意"与"驳回"；缺省不得被当作任一方，否则一次漏参就等于替审核人下了结论
     */
    @OperLog(module = "BCP", operType = "UPDATE", desc = "BCP行级异议审核")
    @RequiresPerm("bcp:objection:review")
    @PostMapping("/{id}/review")
    public Result<Map<String, Object>> review(@PathVariable Long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Boolean approve = toBoolean(b.get("approve"));
        if (approve == null) {
            throw new BizException(400, "缺少 approve（true=同意 / false=驳回）");
        }
        String remark = b.get("remark") == null ? null : String.valueOf(b.get("remark"));
        return Result.success(bcpOpenClient.review(id, approve, currentUser(), remark));
    }

    /** 审核人 = 当前登录用户；随请求体一起被签名覆盖后送 BCP 记录，无法被第三方篡改。 */
    private String currentUser() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUsername();
    }

    /** 宽松布尔解析：接受 JSON 布尔或字符串 "true"/"false"（大小写不敏感）；其余（含缺省）返回 null。 */
    private static Boolean toBoolean(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
