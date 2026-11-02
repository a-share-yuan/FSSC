package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
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
        if (subject == null || subject.getIsLeaf() == null || subject.getIsLeaf() == 1) {
            return subject;
        }
        List<MdSubject> leaves = mapper.selectList(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getParentId, subject.getId())
                .eq(MdSubject::getIsLeaf, 1)
                .eq(MdSubject::getStatus, "NORMAL")
                .orderByAsc(MdSubject::getSubjectCode));
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
}
