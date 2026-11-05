// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 制证科目落末级：凭证分录只能挂末级科目。
 *
 * <p>非末级记账会让明细账与辅助核算汇总失真，过账守卫（{@code VoucherService#post}／
 * {@code batchPost}）同样会拦。本件把「该挂哪个末级」提前到<b>制单时刻</b>定下来，
 * 免得出了一张永远过不了账的凭证：</p>
 * <ul>
 *   <li>末级科目：原样返回；</li>
 *   <li>非末级但只有唯一一个启用的末级下级：下沉到它（纯结构节点，唯一解）；</li>
 *   <li>非末级且有多个明细，但编码登记在 {@link #APPROVED_LEAF_CODE} 里：下沉到登记的明细；</li>
 *   <li>其余（既无唯一解又未登记）：拒绝制证——具体挂哪个明细要用账业务判断，
 *       系统不替人猜，猜错就是把账记到了别的明细科目上。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostableSubjectResolver {

    /**
     * 汇总层编码 → 制证挂账的末级明细（业务批准的口径，2026-10-31）。
     * <p>自动出证的建单代码写的是上级编码（1002/1122/1405/2202/6001），而这些上级下面
     * 挂着多条明细，主数据给不出唯一解 ⇒ 只能按业务口径钉死。钉的是<b>境内</b>那一条：
     * 现下所有自动出证入口（合同/暂估/开票应收/调拨/资产/采购入库）都只走境内本位币业务，
     * 涉外明细没有出证来源。日后真开涉外账，必须由出证侧直接传末级编码，禁止再往本表加键。
     */
    private static final Map<String, String> APPROVED_LEAF_CODE = Map.of(
            "1002", "100201",      // 银行存款 → 基本户
            "1122", "112201",      // 应收账款 → 国内客户款
            "1405", "140501",      // 库存商品 → 原材料
            "2202", "220201",      // 应付账款 → 国内供应商
            "6001", "60010103"     // 主营业务收入 → 国内销售
    );

    private final MdSubjectMapper mapper;

    /**
     * @param subject    待落末级的科目（可为 null，原样返回，交由调用侧按缺科目处理）
     * @param codeForMsg 报错时用的科目编码（调用侧持有的编码字面量，可能比主数据更贴近上下文）
     * @return 可记账的末级科目；入参为 null 时返回 null
     * @throws BizException 该科目非末级且无法唯一确定末级明细
     */
    public MdSubject resolve(MdSubject subject, String codeForMsg) {
        if (subject == null) {
            return null;
        }
        // 末级标记可能被手工改错：以「实际子节点数」为准，有下级就不是末级。
        long children = mapper.countChildren(subject.getId());
        if (subject.getIsLeaf() != null && subject.getIsLeaf() == 1 && children == 0) {
            return subject;
        }
        LambdaQueryWrapper<MdSubject> childQw = new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getParentId, subject.getId())
                .eq(MdSubject::getIsLeaf, 1)
                .eq(MdSubject::getStatus, "NORMAL")
                .orderByAsc(MdSubject::getSubjectCode);
        if (subject.getCompanyId() != null) {
            Long companyId = subject.getCompanyId();
            childQw.and(w -> w.eq(MdSubject::getCompanyId, companyId).or().isNull(MdSubject::getCompanyId));
        }
        List<MdSubject> leaves = mapper.selectList(childQw);
        if (leaves.size() == 1) {
            MdSubject leaf = leaves.get(0);
            log.info("科目 {} {} 非末级，制证下沉到唯一末级明细 {} {}",
                    subject.getSubjectCode(), subject.getSubjectName(),
                    leaf.getSubjectCode(), leaf.getSubjectName());
            return leaf;
        }
        String approvedCode = APPROVED_LEAF_CODE.get(subject.getSubjectCode());
        if (approvedCode != null) {
            for (MdSubject leaf : leaves) {
                if (approvedCode.equals(leaf.getSubjectCode())) {
                    log.info("科目 {} {} 非末级（下设 {} 个明细），制证按登记口径下沉到 {} {}",
                            subject.getSubjectCode(), subject.getSubjectName(), leaves.size(),
                            leaf.getSubjectCode(), leaf.getSubjectName());
                    return leaf;
                }
            }
        }
        String code = codeForMsg == null || codeForMsg.isBlank() ? subject.getSubjectCode() : codeForMsg;
        String why = leaves.isEmpty()
                ? "没有启用的末级明细科目"
                : "下设 " + leaves.size() + " 个明细科目，无法确定用哪一个";
        throw new BizException(ErrorCode.PARAM_INVALID,
                "科目 " + code + " " + subject.getSubjectName() + " 不是末级科目（" + why
                        + "），无法制证；请先在【会计科目】维护末级明细科目后再制证");
    }

    /**
     * 费用行制证科目前置校验（提交预检与制证共用）：
     * 行科目 → 费用项绑定科目；缺失、停用、跨公司或无法落末级一律拒绝，并点名费用项与配置入口。
     *
     * @param item         费用行对应的费用项目（可空，空时只认行科目）
     * @param lineSubjectId 费用行上直接选的科目（优先）
     * @param companyId    单据所属公司（用于跨公司校验；空则不做公司维度校验）
     * @return 可记账的末级科目
     */
    public MdSubject requireExpenseSubject(MdExpenseItem item, Long lineSubjectId, Long companyId) {
        Long candidate = lineSubjectId != null ? lineSubjectId : (item == null ? null : item.getSubjectId());
        String itemLabel = item == null ? "本行费用" : "费用项目【" + item.getItemName() + "】";
        if (candidate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    itemLabel + "未配置对应会计科目，无法制证；请到【基础资料›费用项目】配置末级科目后重新生成");
        }
        return requireSubject(candidate, companyId, itemLabel, "【基础资料›费用项目】");
    }

    /**
     * 通用制证科目校验：存在、启用、非他公司、可落末级。规则池（{@code md_gl_config}）
     * 与费用项映射共用，防止任一侧配置把凭证记到他公司账套或父科目。
     *
     * @param subjectId 目标科目主键（非空）
     * @param companyId 单据所属公司（空则不做公司维度校验）
     * @param label     报错点名的配置对象（费用项／记账规则）
     * @param configEntry 配置入口文案
     * @return 可记账的末级科目
     */
    public MdSubject requireSubject(Long subjectId, Long companyId, String label, String configEntry) {
        MdSubject subject = mapper.selectById(subjectId);
        if (subject == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    label + "指向的会计科目不存在或已删除，无法制证；请到" + configEntry + "重新配置末级科目");
        }
        if (!"NORMAL".equals(subject.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    label + "指向的会计科目 " + subject.getSubjectCode() + " " + subject.getSubjectName()
                            + " 已停用，无法制证；请到" + configEntry + "改配启用中的末级科目");
        }
        if (companyId != null && subject.getCompanyId() != null && !companyId.equals(subject.getCompanyId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    label + "指向的会计科目 " + subject.getSubjectCode() + " " + subject.getSubjectName()
                            + " 属于其他公司，不能用于本公司凭证；请到" + configEntry + "改配本公司或租户共用科目");
        }
        return resolve(subject, subject.getSubjectCode());
    }
}
