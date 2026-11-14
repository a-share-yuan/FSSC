package com.fssc.masterdata.service;

import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 记账规则池的制证科目来源同样受末级/停用/跨公司闸约束。 */
@DisplayName("制证科目通用校验：跨公司与末级")
class PostableSubjectResolverTest {

    private final MdSubjectMapper mapper = Mockito.mock(MdSubjectMapper.class);
    private final PostableSubjectResolver resolver = new PostableSubjectResolver(mapper);

    private static MdSubject subject(long id, String code, String name, Long companyId) {
        MdSubject s = new MdSubject();
        s.setId(id);
        s.setSubjectCode(code);
        s.setSubjectName(name);
        s.setCompanyId(companyId);
        s.setIsLeaf(1);
        s.setStatus("NORMAL");
        return s;
    }

    @Test
    @DisplayName("规则指向他公司科目 → 拒绝，并点名配置入口")
    void 跨公司科目被拒() {
        Mockito.when(mapper.selectById(9L))
                .thenReturn(subject(9L, "6602", "管理费用-差旅费", 103L));

        BizException ex = assertThrows(BizException.class,
                () -> resolver.requireSubject(9L, 1L, "记账规则（配置ID 3489）", "【基础资料›记账配置】"));
        assertTrue(ex.getMessage().contains("属于其他公司"), "应点名跨公司，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("3489"), "应点名配置行，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("租户共用科目 → 放行；本公司科目 → 放行")
    void 本公司及租户共用科目放行() {
        MdSubject shared = subject(8L, "660301", "财务费用-手续费", null);
        Mockito.when(mapper.selectById(8L)).thenReturn(shared);
        assertSame(shared, resolver.requireSubject(8L, 1L, "记账规则（配置ID 1）", "【基础资料›记账配置】"));

        MdSubject own = subject(7L, "660205", "管理费用-劳动保护费", 1L);
        Mockito.when(mapper.selectById(7L)).thenReturn(own);
        assertEquals(7L, resolver.requireSubject(7L, 1L, "记账规则（配置ID 2）", "【基础资料›记账配置】").getId());
    }

    @Test
    @DisplayName("停用科目 → 拒绝")
    void 停用科目被拒() {
        MdSubject stopped = subject(6L, "660206", "管理费用-业务招待费", 1L);
        stopped.setStatus("STOP");
        Mockito.when(mapper.selectById(6L)).thenReturn(stopped);

        BizException ex = assertThrows(BizException.class,
                () -> resolver.requireSubject(6L, 1L, "记账规则（配置ID 3）", "【基础资料›记账配置】"));
        assertTrue(ex.getMessage().contains("已停用"), "实际: " + ex.getMessage());
    }
}
