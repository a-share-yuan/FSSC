package com.fssc.expense.service;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpTravel;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpTravelMapper;
import com.fssc.l1.L1TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D-V4R-33：差旅行「市内交通费」必须既落库、又进兜底合计。
 *
 * <p>实案（2026-10-13 12:33 运行态）：出差申请 {@code SQ202610130007} 五段金额填 1,200／2,400／900／600／900，
 * 前端「小计」＝6,000，而落库行的分量只有 1,200＋2,400＋900＋900＝<b>5,400</b>——
 * 因为表与实体都没有市内交通费这一栏（UI 绑的是 {@code row.localTrafficAmount}），
 * 兜底合计也只加四项 ⇒ 同一行自己跟自己对不上，重开草稿小计还会从 6,000 缩成 5,400。</p>
 */
@DisplayName("差旅行市内交通费：落库与兜底合计同口径")
class TravelLocalTrafficSumTest {

    private static final Long BILL_ID = 9101L;

    private ExpenseDetailService svc;
    private ExpTravelMapper expTravelMapper;

    @BeforeEach
    void setUp() {
        svc = L1TestSupport.mockService(ExpenseDetailService.class);
        expTravelMapper = Mockito.mock(ExpTravelMapper.class);
        ExpBillMapper expBillMapper = Mockito.mock(ExpBillMapper.class);
        ExpBill bill = new ExpBill();
        bill.setId(BILL_ID);
        bill.setStatus("DRAFT");
        Mockito.when(expBillMapper.selectById(BILL_ID)).thenReturn(bill);
        Mockito.when(expTravelMapper.delete(Mockito.any())).thenReturn(1);
        Mockito.when(expTravelMapper.insert(Mockito.any(ExpTravel.class))).thenReturn(1);
        L1TestSupport.inject(svc, "expTravelMapper", expTravelMapper);
        L1TestSupport.inject(svc, "expBillMapper", expBillMapper);
    }

    private ExpTravel row() {
        ExpTravel t = new ExpTravel();
        t.setTravelerName("报销员工");
        t.setTrafficAmount(new BigDecimal("1200"));
        t.setHotelAmount(new BigDecimal("2400"));
        t.setAllowanceAmount(new BigDecimal("900"));
        t.setLocalTrafficAmount(new BigDecimal("600"));
        t.setOtherAmount(new BigDecimal("900"));
        return t;
    }

    private ExpTravel saved(ExpTravel in) {
        svc.saveTravels(BILL_ID, List.of(in));
        ArgumentCaptor<ExpTravel> cap = ArgumentCaptor.forClass(ExpTravel.class);
        Mockito.verify(expTravelMapper).insert(cap.capture());
        return cap.getValue();
    }

    /** 兜底合计必须含市内交通费：五项之和 6,000（改造前只加四项＝5,400，本例当场红）。 */
    @Test
    void totalFallbackIncludesLocalTraffic() {
        ExpTravel t = row();
        t.setTotalAmount(null);
        assertEquals(0, new BigDecimal("6000.00").compareTo(saved(t).getTotalAmount()),
                "兜底合计漏了市内交通费就会只算出 5,400");
    }

    /** 这一栏要真落库（被实体丢字段），否则分量与合计永远对不上。 */
    @Test
    void localTrafficAmountSurvivesToTheSavedRow() {
        ExpTravel t = row();
        t.setTotalAmount(null);
        assertEquals(0, new BigDecimal("600").compareTo(saved(t).getLocalTrafficAmount()),
                "市内交通费没被带进落库行＝列或字段又丢了");
    }

    /** 前端已把小计算好回传时，服务端覆盖它（保持"前端口径优先"这条既有行为）。 */
    @Test
    void clientSuppliedTotalIsNotOverwritten() {
        ExpTravel t = row();
        t.setTotalAmount(new BigDecimal("7000"));
        assertEquals(0, new BigDecimal("7000").compareTo(saved(t).getTotalAmount()));
    }

    /** 反证面：这栏为空时按 0 加，兜底合计仍是四项之和（新加的那项不得凭空造数）。 */
    @Test
    void nullLocalTrafficAddsZero() {
        ExpTravel t = row();
        t.setLocalTrafficAmount(null);
        t.setTotalAmount(null);
        assertEquals(0, new BigDecimal("5400").compareTo(saved(t).getTotalAmount()));
    }
}
