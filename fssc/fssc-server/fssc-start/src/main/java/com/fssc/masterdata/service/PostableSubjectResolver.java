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

/**
 * 制证科目落末级：凭证分录只能挂末级科目。
 *
 * <p>非末级记账会让明细账与辅助核算汇总失真，过账守卫（{@code VoucherService#post}／
 * {@code batchPost}）同样会拦。本件把「该挂哪个末级」提前到<b>制单时刻</b>定下来，
 * 免得出了一张永远过不了账的凭证：</p>
 * <ul>
 *   <li>末级科目：原样返回；</li>
 *   <li>非末级但只有唯一一个启用的末级下级：下沉到它（纯结构节点，唯一解）；</li>
 *   <li>其余（有多个明细／没有明细）：拒绝制证——具体挂哪个明细要用账业务判断，
 *       系统不替人猜，猜错就是把账记到了别的明细科目上。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostableSubjectResolver {

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
        String code = codeForMsg == null || codeForMsg.isBlank() ? subject.getSubjectCode() : codeForMsg;
        String why = leaves.isEmpty()
                ? "没有启用的末级明细科目"
                : "下设 " + leaves.size() + " 个明细科目，无法确定用哪一个";
        throw new BizException(ErrorCode.PARAM_INVALID,
                "科目 " + code + " " + subject.getSubjectName() + " 不是末级科目（" + why
                        + "），无法制证；请先在【会计科目】维护末级明细科目后再制证");
    }
}
