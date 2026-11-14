package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fssc.expense.dto.LoanCandidateDTO;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillRelationMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.l1.L1TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 借款冲抵候选面必须吐出「可冲抵余额（账面 − 在途占用）」。
 *
 * <p><b>为什么这是缺陷而不是口径差</b>：{@code ExpBill.loanBalance} 是账面余额，不含在途占用；
 * 候选面把它当"可冲抵额"交给前端，前端默认勾全额 ⇒ 第二张草稿必然撞
 * {@code registerLoanOccupancy} 的服务端超额闸（该方法类注释里登记的"已知在册残口①"）。
 * 本用例把三条口径钉死：① 无在途时 available＝账面；② 有在途时 available＝账面−在途；
 * ③ 在途占满的行不出现在候选里（选了也是死路）；④ 候选只列当前用户本人名下。</p>
 */
@DisplayName("借款候选面：可冲抵余额＝账面−在途占用")
class LoanCandidatesAvailableBalanceTest {

    private static final Long ME = 3808L;

    private ExpenseDetailService svc;
    private ExpBillMapper billMapper;
    private ExpBillRelationMapper relationMapper;

    @BeforeEach
    void setUp() throws Exception {
        // LambdaQueryWrapper 渲染 SQL 需要实体列名缓存（TableInfo）；纯单测环境没有 MyBatis 扫描，
        // 这里手工初始化一次，用例④ 才能真正读到 wrapper 里的过滤参数（offline 可跑）。
        try {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                    ExpBill.class);
        } catch (Exception ignore) {
            // 已初始化过则忽略：用例④ 之外的四条不依赖它
        }

        svc = L1TestSupport.mockService(ExpenseDetailService.class);
        billMapper = Mockito.mock(ExpBillMapper.class);
        relationMapper = Mockito.mock(ExpBillRelationMapper.class);
        L1TestSupport.inject(svc, "expBillMapper", billMapper);
        L1TestSupport.inject(svc, "expBillRelationMapper", relationMapper);

        LoginUser u = new LoginUser();
        u.setUserId(ME);
        UserContext.set(u);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void stubLoans(ExpBill... loans) {
        when(billMapper.selectList(ArgumentMatchers.<Wrapper<ExpBill>>any())).thenReturn(List.of(loans));
    }

    /** 在途登记：target 单处于 DRAFT/APPROVING 才算占用（与 transitLoanOccupancy 同口径） */
    private void stubTransit(Long targetBillId, String targetStatus, String useAmount) {
        ExpBillRelation rel = new ExpBillRelation();
        rel.setSourceBillId(11L);
        rel.setTargetBillId(targetBillId);
        rel.setRelationType("LOAN_CLAIM");
        rel.setUseAmount(new BigDecimal(useAmount));
        when(relationMapper.selectList(any())).thenReturn(List.of(rel));

        ExpBill target = new ExpBill();
        target.setId(targetBillId);
        target.setStatus(targetStatus);
        when(billMapper.selectById(targetBillId)).thenReturn(target);
    }

    private static ExpBill loan(long id, String no, String book) {
        ExpBill b = new ExpBill();
        b.setId(id);
        b.setBillNo(no);
        b.setBillType("LOAN");
        b.setStatus("APPROVED");
        b.setTitle("差旅借款-" + no);
        b.setApplyUserId(ME);
        b.setTotalAmount(new BigDecimal(book));
        b.setLoanBalance(new BigDecimal(book));
        return b;
    }

    @Test
    @DisplayName("① 无在途占用：可冲抵余额＝账面余额")
    void availableEqualsBookWhenNoTransit() {
        stubLoans(loan(11L, "JK202610190003", "5000.00"));
        stubTransit(99L, "APPROVED", "1200.00");   // 已审批通过 → 冲抵额已落流水扣进账面，不重复扣

        List<LoanCandidateDTO> rows = svc.loanCandidates(1L, 50);

        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("5000.00").compareTo(rows.get(0).getAvailableBalance()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(rows.get(0).getLoanBalance()));
    }

    @Test
    @DisplayName("② 有在途占用：可冲抵余额＝账面−在途（旧候选面会给全额→必被服务端闸拒）")
    void availableSubtractsTransit() {
        stubLoans(loan(11L, "JK202610190003", "5000.00"));
        stubTransit(98L, "DRAFT", "2000.00");      // 另一张草稿已在途占用 2,000

        List<LoanCandidateDTO> rows = svc.loanCandidates(1L, 50);

        assertEquals(1, rows.size());
        LoanCandidateDTO dto = rows.get(0);
        assertEquals(0, new BigDecimal("5000.00").compareTo(dto.getLoanBalance()), "账面余额不变");
        assertEquals(0, new BigDecimal("3000.00").compareTo(dto.getAvailableBalance()), "可冲抵＝账面−在途");
        assertTrue(dto.getAvailableBalance().compareTo(dto.getLoanBalance()) < 0,
                "有在途时 available 必须严格小于账面，否则等于没修");
    }

    @Test
    @DisplayName("③ 在途已占满的行不返回（无可冲抵，列出来也是死路）")
    void fullyOccupiedRowIsDropped() {
        stubLoans(loan(11L, "JK202610190003", "1000.00"));
        stubTransit(97L, "APPROVING", "1000.00");  // 在途占满

        List<LoanCandidateDTO> rows = svc.loanCandidates(1L, 50);

        assertTrue(rows.isEmpty(), "available≤0 的候选不得出现在选择面里");
    }

    @Test
    @DisplayName("④ 候选只列当前登录用户本人名下（服务端本就只允许冲销本人借款）")
    void candidatesAreScopedToCurrentUser() {
        ArgumentCaptor<Wrapper<ExpBill>> cap = ArgumentCaptor.forClass(Wrapper.class);
        when(billMapper.selectList(cap.capture())).thenReturn(List.of());
        stubTransit(96L, "DRAFT", "0.00");

        svc.loanCandidates(1L, 50);

        // LambdaQueryWrapper 的参数表是「渲染 SQL 时才填」的，必须先触发一次渲染再读
        Wrapper<ExpBill> w = cap.getValue();
        w.getSqlSegment();
        java.util.Map<String, Object> params =
                ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) w).getParamNameValuePairs();
        assertTrue(params.containsValue(ME),
                "候选查询必须按 apply_user_id＝当前用户 过滤，实际参数=" + params);
    }

    @Test
    @DisplayName("⑤ 未登录（UserContext 空）返回空列表，不抛异常")
    void anonymousGetsEmptyList() {
        UserContext.clear();
        List<LoanCandidateDTO> rows = svc.loanCandidates(1L, 50);
        assertTrue(rows.isEmpty());
    }
}
