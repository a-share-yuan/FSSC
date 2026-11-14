package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字段级表单权限（form_perm）服务端校验。
 *
 * <p><b>为什么需要</b>：{@code wf_node_config.form_perm}（扁平 {@code {"field":"H|R|W"}}）原先
 * 只有一条"只读回显"链——{@link WfNodeConfigResolver#resolveFormFieldPerm} 解析后经
 * {@code GET /wf/task-form-perm} 交前端 {@code BillDetailPanel.vue} 做隐藏/只读渲染。
 * 前端裁剪<b>拦不住直接调接口</b>：绕过页面即可提交被本环节隐藏/只读的字段。本类把该权限
 * 下沉为<b>服务端硬校验</b>，堵住这条越权面。
 *
 * <p><b>语义</b>（档位解析与"取严不取宽"已在解析器内完成，本类只判定）：
 * <ul>
 *  <li>H（隐藏）：本环节不可见的字段，<b>禁止出现在提交载荷中</b>；</li>
 *  <li>R（只读）：本环节只读的字段，<b>禁止变更</b>（本类按"出现在提交载荷即视为变更"从严，
 *      因此调用方只传本次真正要写入的字段）；</li>
 *  <li>W（可编辑）/ 未配置：放行。</li>
 * </ul>
 *
 * <p><b>fail-open 边界</b>（与解析器同一规则，保持历史行为零变更）：
 * 无流程实例 / 无在办任务 / 任务缺 {@code modelKey:nodeKey} / form_perm 为空 → <b>直接放行</b>。
 * 因此本校验<b>只在显式配置了 form_perm 的节点上生效</b>；全库当前 form_perm 为空 ⇒ 接入后零行为差异。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfFormPermGuard {

    private final WfNodeConfigResolver wfNodeConfigResolver;
    private final WfTaskMapper wfTaskMapper;

    /**
     * 按单据流程实例做审批上下文字段级写入校验；命中 H/R 抛 {@link BizException}（FORBIDDEN）。
     *
     * @param procInstId 业务单据的流程实例 ID（如 {@code exp_bill.proc_inst_id}）；空 → 放行
     * @param submittedFields 本次将要写入的字段名 → 值；值为 null 的条目跳过
     */
    public void assertFieldsWritable(String procInstId, Map<String, Object> submittedFields) {
        if (procInstId == null || procInstId.isBlank() || submittedFields == null || submittedFields.isEmpty()) {
            return;
        }
        WfTask task = wfTaskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getProcInstId, procInstId)
                .eq(WfTask::getStatus, "PENDING")
                .last("LIMIT 1"));
        if (task == null || task.getModelKey() == null || task.getNodeKey() == null) {
            return; // 无在办任务（未起流程/已办结）/ 镜像缺列 → fail-open
        }
        Map<String, String> perm = wfNodeConfigResolver.resolveFormFieldPerm(task.getModelKey(), task.getNodeKey());
        if (perm == null || perm.isEmpty()) {
            return; // 本节点未配 form_perm → fail-open
        }
        for (Map.Entry<String, Object> e : submittedFields.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String code = perm.get(e.getKey());
            if (code == null) {
                continue; // 该字段未受控 → 放行
            }
            if ("H".equals(code)) {
                log.warn("form_perm 拦截：字段 {} 在节点 {}({}) 为隐藏档，禁止提交",
                        e.getKey(), task.getNodeKey(), task.getModelKey());
                throw new BizException(ErrorCode.FORBIDDEN,
                        "字段「" + e.getKey() + "」在本审批环节不可见，禁止提交；如需调整请联系流程管理员核对节点字段权限");
            }
            if ("R".equals(code)) {
                log.warn("form_perm 拦截：字段 {} 在节点 {}({}) 为只读档，禁止变更",
                        e.getKey(), task.getNodeKey(), task.getModelKey());
                throw new BizException(ErrorCode.FORBIDDEN,
                        "字段「" + e.getKey() + "」在本审批环节为只读，禁止变更");
            }
        }
    }

    /** 便捷构造：把若干「字段名, 值」对装进有序 Map（null 值由本类跳过），用于调用点保持简洁。 */
    public static Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
