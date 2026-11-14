package com.fssc.image.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把「统计口径」和「索引必须配套 FORCE INDEX」钉在 SQL 文本上。
 *
 * <p>2026-11-14 压测发现 {@code statByBizType} 判定 {@code PASSED/DONE}，
 * 而代码实际写入 {@code OK/SEALED} ⇒ 五个 bizType 的 reviewedCnt/sealedCnt 恒为 0，
 * 接口返回 200 且不报错，属静默错数。没有 DB 的单测无法查数据，但能防止有人改回错枚举。</p>
 */
class ImgBatchMapperSqlTest {

    private static String sqlOf(String method, Class<?>... paramTypes) throws Exception {
        Method m = ImgBatchMapper.class.getMethod(method, paramTypes);
        Select a = m.getAnnotation(Select.class);
        return Arrays.stream(a.value()).collect(Collectors.joining());
    }

    @Test
    void 统计必须用代码真实写入的枚举() throws Exception {
        String sql = sqlOf("statByBizType", Long.class);
        assertTrue(sql.contains("review_status = 'OK'"), "代码白名单是 PENDING/OK/REJECTED");
        assertTrue(sql.contains("seal_status = 'SEALED'"), "代码写入的是 UNSEALED/SEALED");
        assertFalse(sql.contains("'PASSED'"), "PASSED 不是合法枚举，会让 reviewedCnt 恒 0");
        assertFalse(sql.contains("'DONE'"), "DONE 不是合法枚举，会让 sealedCnt 恒 0");
    }

    @Test
    void 封单计数必须强制走专用索引() throws Exception {
        String sql = sqlOf("countBySealStatus", Long.class, String.class);
        assertTrue(sql.contains("FORCE INDEX (idx_ib_tenant_del_seal)"),
                "优化器默认不选该索引（实测 305ms vs FORCE 后 126ms），不 FORCE 等于索引零收益");
    }
}
