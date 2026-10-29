// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfBusinessFlow;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.mapper.WfBusinessFlowMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

/** 流程模型（wf_model，BPMN 草稿，带 tenant_id）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfModelService {

    private final WfModelMapper modelMapper;
    private final WfBusinessFlowMapper wfBusinessFlowMapper;
    private final WfBpmnTemplateService bpmnTemplateService;
    private final RepositoryService repositoryService;
    /** 每次真实部署把 XML 快照 + 统一 diff 落到仓库目录，流程变更可追溯 */
    private final WfBpmnRepoService bpmnRepoService;
    /** 发布时统计在途旧实例，写进发布日志 */
    private final WfQueryService wfQueryService;
    /** A4：assign_type 可解析性 fast-fail——部署前拒绝"无默认方案的 POST/EXPRESSION 节点" */
    private final WfAssigneeTypeGuard assigneeTypeGuard;

    public PageResult<WfModel> page(long pageNum, long pageSize, String keyword, String category, String status) {
        LambdaQueryWrapper<WfModel> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(WfModel::getModelKey, keyword)
                    .or().like(WfModel::getModelName, keyword));
        }
        if (category != null && !category.isBlank()) {
            qw.eq(WfModel::getCategory, category);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(WfModel::getStatus, status);
        }
        qw.orderByDesc(WfModel::getId);
        Page<WfModel> p = modelMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public WfModel detail(Long id) {
        WfModel m = modelMapper.selectById(id);
        if (m == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在: " + id);
        }
        return m;
    }

    /**
     * 必填办结（批次 2C，与前端 FlowConfig「基本信息」表单 / GenericList 流程模型弹窗同一规则）：
     * modelKey 既是 BPMN 的 process key，也是 wf_node_config / wf_process_config 的关联键，留空即挂不上节点。
     */
    private void validateKeyAndName(WfModel model) {
        AssertUtil.notBlank(model.getModelKey(), ErrorCode.PARAM_INVALID, "请输入模型编码");
        AssertUtil.notBlank(model.getModelName(), ErrorCode.PARAM_INVALID, "请输入模型名称");
    }

    public Long create(WfModel model) {
        validateKeyAndName(model);
        long dup = modelMapper.selectCount(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, model.getModelKey()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型标识已存在: " + model.getModelKey());
        }
        model.setId(null);
        if (model.getVersion() == null) {
            model.setVersion(1);
        }
        if (model.getStatus() == null) {
            model.setStatus("DRAFT");
        }
        modelMapper.insert(model);
        return model.getId();
    }

    public void update(WfModel model) {
        WfModel old = modelMapper.selectById(model.getId());
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在: " + model.getId());
        }
        // 整单更新（前端提交完整对象），故与 create 同一规则拦空；publish() 走独立对象不经过本方法
        validateKeyAndName(model);
        boolean redeploy = model.getBpmnXml() != null && !model.getBpmnXml().isBlank()
                && !model.getBpmnXml().equals(old.getBpmnXml());
        // A4 fast-fail：仅重部署路径校验（与 publish 同一规则），且同样先于写库，
        // 避免"库已改、部署被拒"的不一致态；纯字段更新（不重部署）不受影响。
        if (redeploy) {
            assigneeTypeGuard.assertModelAssigneesResolvable(old.getModelKey());
        }
        modelMapper.updateById(model);
        // 流程定义变化 → 重新部署（新版本），保证业务即时使用最新流程
        if (redeploy) {
            deployToEngine(model, old);
        }
    }

    /**
     * 发布（UC09）：写入 BPMN 并<b>无条件</b>重新部署到 Flowable。
     *
     * <p>区别于 {@link #update} 的「仅当内容变化才部署」——发布是用户的显式动作，即便内容与
     * 库中相同也应保证引擎里存在可用的流程定义。
     *
     * <p><b>修复背景（2026-09-20 实测）</b>：设计器 {@code /wf/designer/publish} 原实现直接
     * {@code modelMapper.updateById}，绕过了本 Service 的部署分支，导致
     * {@code wf_model.deploy_id} 长期为 NULL —— FA_DISPOSE / INV_DISPOSE / CLAIM_G1 等
     * {@code source=TEMPLATE} 的流程<b>从未真正部署进引擎</b>（实例数恒为 0），改了 BPMN 也不生效。
     * 本次改由 Controller 调用本方法，使"发布"名副其实。
     *
     * @param model 含 id 与 bpmnXml；version 由本方法在部署成功后 +1（调用方无需预设）
     */
    public void publish(WfModel model) {
        WfModel old = modelMapper.selectById(model.getId());
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在: " + model.getId());
        }
        if (model.getBpmnXml() == null || model.getBpmnXml().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发布失败：BPMN 内容为空");
        }
        // A4 fast-fail：必须在写库之前校验——publish 先 updateById 再部署，
        // 若部署前才抛错会留下"库中新 XML、引擎中旧定义"的不一致态。
        assigneeTypeGuard.assertModelAssigneesResolvable(old.getModelKey());
        modelMapper.updateById(model);
        deployToEngine(model, old);
    }

    /** 部署到 Flowable 并回写 deploy_id / proc_def_id / version */
    private void deployToEngine(WfModel model, WfModel old) {
        try {
            String key = old.getModelKey();
            Deployment dep = repositoryService.createDeployment()
                    .addString(key + ".bpmn20.xml", model.getBpmnXml())
                    .name(model.getModelName() == null ? key : model.getModelName())
                    .deploy();
            var pd = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(dep.getId()).singleResult();
            WfModel upd = new WfModel();
            upd.setId(model.getId());
            upd.setDeployId(dep.getId());
            upd.setProcDefId(pd == null ? null : pd.getId());
            upd.setVersion(old.getVersion() == null ? 2 : old.getVersion() + 1);
            modelMapper.updateById(upd);
            log.info("流程模型已重新部署: key={}, version={}, deploymentId={}",
                    key, upd.getVersion(), dep.getId());
            // 仓库化落盘 + 在途旧实例计数：属可追溯性增强，任何失败都不回滚发布、不影响业务
            try {
                long inFlight = wfQueryService.countRunningOld(key, pd == null ? null : pd.getId());
                bpmnRepoService.recordPublish(old, model.getBpmnXml(), upd.getVersion(),
                        dep.getId(), pd == null ? null : pd.getId(), (int) inFlight);
                if (inFlight > 0) {
                    log.warn("发布后仍有 {} 个在途实例按旧版本流程执行 modelKey={} newProcDefId={}",
                            inFlight, key, pd == null ? null : pd.getId());
                }
            } catch (Exception repoEx) {
                log.warn("BPMN 发布记录/在途统计失败 modelKey={}", key, repoEx);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception ex) {
            log.error("流程模型重新部署失败 modelKey={}", old.getModelKey(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID, "BPMN 部署失败: " + ex.getMessage());
        }
    }

    /** 模板同步：把 wf_business_flow（需求标准流程）合并为 wf_model 模型（source=TEMPLATE）；已存在则刷新 bpmn_xml（模板生成规则变化时生效） */
    @PostConstruct
    public void initSync() {
        TenantContext.runIgnoreTenant(this::syncFromTemplates);
    }

    public void syncFromTemplates() {
        try {
            List<WfBusinessFlow> flows = wfBusinessFlowMapper.selectList(null);
            for (WfBusinessFlow f : flows) {
                if (f.getNodeJson() == null || f.getNodeJson().isBlank()) continue;
                String key = f.getBizType();
                String xml = bpmnTemplateService.build(f.getFlowName(), key, f.getNodeJson());
                WfModel exist = modelMapper.selectOne(new LambdaQueryWrapper<WfModel>()
                        .eq(WfModel::getModelKey, key).last("limit 1"));
                if (exist != null) {
                    // 已存在：仅当 BPMN 生成规则变化（内容不同）时刷新，保持版本号+1
                    if (exist.getBpmnXml() == null || !exist.getBpmnXml().equals(xml)) {
                        WfModel upd = new WfModel();
                        upd.setId(exist.getId());
                        upd.setBpmnXml(xml);
                        upd.setVersion(exist.getVersion() == null ? 2 : exist.getVersion() + 1);
                        modelMapper.updateById(upd);
                        log.info("模板模型刷新 bpmn: key={}, version={}", key, upd.getVersion());
                    }
                    continue;
                }
                WfModel m = new WfModel();
                m.setTenantId(f.getTenantId());
                m.setModelKey(key);
                m.setModelName(f.getFlowName());
                // category 留空＝未归业务域。曾经写死 "TEMPLATE"，那是 source 的取值串进了分类列，
                // 导致流程配置页「分类」显示成别的字段的内容（历史 18 行同因，需数据侧订正）。
                m.setBizType(key);
                m.setSource("TEMPLATE");
                m.setBpmnXml(xml);
                m.setVersion(1);
                m.setStatus("ACTIVE");
                modelMapper.insert(m);
            }
        } catch (Exception ex) {
            log.error("业务流程模板同步到模型失败", ex);
        }
    }

    public void enable(Long id, boolean enable) {
        WfModel m = new WfModel();
        m.setId(id);
        m.setStatus(enable ? "DEPLOYED" : "DISABLED");
        modelMapper.updateById(m);
    }

    /**
     * 删除模型（物理删，删后释放 model_key 唯一键，见 {@code WfModelMapper.hardDelete} 注释）。
     *
     * <p><b>先拦在途实例。</b>原实现一句 {@code modelMapper.hardDelete(id)} 裸删，
     * 于是「删模型带在途单」：wf_model 行没了，在跑实例的 {@code proc_inst_id} 仍在引擎里推进，
     * 而节点配置按 model_key 关联（{@code wf_node_config}/{@code wf_process_config}）从此解析不到——
     * 催办/超时/改派这类按节点配置驱动的动作静默失效，单据既不推进也不报警，只在待办里发臭
     * 册载"节点配置失效永不催办"。修法取<b>禁删</b>而非软删：本表 status 名称表
     * （DRAFT/ACTIVE/DEPLOYED/DISABLED，同 {@link #enable}）里没有"已删除"一格，
     * 新造状态会污染所有按 status 过滤的读侧，而"停用"诉求 {@code enable(id,false)} 已是独立动作。
     *
     * <p>在途规则复用类内既有 {@code wfQueryService.countRunningOld(modelKey, null)}
     * （＝{@code wf_instance.model_key = 本模型 && status='RUNNING'}，发布时统计在途旧实例用的
     * 同一个方法、同一张表，不再另立第二套规则）；租户条件由拦截器注入，只数本租户自己的实例。
     *
     * <p>【不做的事】不级联删节点配置、不替用户终止在途实例——那是两码事，需要人工决定。
     */
    public void delete(Long id) {
        WfModel m = modelMapper.selectById(id);
        if (m == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模型不存在: " + id);
        }
        long inFlight = wfQueryService.countRunningOld(m.getModelKey(), null);
        if (inFlight > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该模型有 " + inFlight + " 个在途实例（wf_instance.status=RUNNING），禁止删除："
                            + "删除会让节点配置按 model_key 失效、在途单既不催办也不推进。"
                            + "请先办结或终止在途实例；只是不想再被使用的话请改用「停用」。modelKey="
                            + m.getModelKey());
        }
        modelMapper.hardDelete(id);
        log.info("流程模型已删除 modelKey={} id={}（删除前在途实例数=0）", m.getModelKey(), id);
    }
}
