package com.fssc.l1;

import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.service.VoucherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * VoucherService 单元测试：凭证分录的借贷平衡不变量。
 *
 * <p>会计恒等式：Σ借方 == Σ贷方。任何生成路径都必须守住该不变量，
 * 否则凭证无法过账（op_voucher.debit_total / credit_total 由分录汇总推导）。
 */
@DisplayName("L1-B-02 VoucherService 借贷平衡")
class L1B02VoucherServiceTest {

    private static OpVoucherLine line(String direction, String amount) {
        OpVoucherLine l = new OpVoucherLine();
        l.setDirection(direction);
        l.setAmount(new BigDecimal(amount));
        l.setSubjectId(1L);
        return l;
    }

    private static VoucherService svcWith(List<OpVoucherLine> stub) {
        VoucherService svc = L1TestSupport.mockService(VoucherService.class);
        OpVoucherMapper vm = mock(OpVoucherMapper.class);
        OpVoucher v = new OpVoucher();
        v.setId(1L);
        v.setCompanyId(1L);
        when(vm.selectById(anyLong())).thenReturn(v);
        L1TestSupport.inject(svc, "opVoucherMapper", vm);

        OpVoucherLineMapper lm = mock(OpVoucherLineMapper.class);
        when(lm.selectByVoucherId(anyLong())).thenReturn(stub);
        L1TestSupport.inject(svc, "opVoucherLineMapper", lm);
        return svc;
    }

    private static BigDecimal sum(List<OpVoucherLine> lines, String direction) {
        return lines.stream().filter(x -> direction.equals(x.getDirection()))
                .map(OpVoucherLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("一借一贷: 借 800 / 贷 800 平衡")
    void 一借一贷平衡() {
        List<OpVoucherLine> lines = svcWith(Arrays.asList(
                line("DEBIT", "800"), line("CREDIT", "800"))).lines(1L);
        assertEquals(2, lines.size());
        BigDecimal dr = sum(lines, "DEBIT");
        BigDecimal cr = sum(lines, "CREDIT");
        assertEquals(0, dr.compareTo(cr), "借贷必须相等，实际 借=" + dr + " 贷=" + cr);
        assertEquals(0, new BigDecimal("800").compareTo(dr));
    }

    @Test
    @DisplayName("多借一贷: 借 300+500 / 贷 800 平衡")
    void 多借一贷平衡() {
        List<OpVoucherLine> lines = svcWith(Arrays.asList(
                line("DEBIT", "300"), line("DEBIT", "500"), line("CREDIT", "800"))).lines(2L);
        BigDecimal dr = sum(lines, "DEBIT");
        BigDecimal cr = sum(lines, "CREDIT");
        assertEquals(0, dr.compareTo(cr));
        assertEquals(0, new BigDecimal("800").compareTo(dr));
    }

    @Test
    @DisplayName("空凭证: 返回空分录")
    void 空凭证() {
        List<OpVoucherLine> lines = svcWith(Collections.emptyList()).lines(3L);
        assertTrue(lines.isEmpty());
    }
}
