package com.fssc.platform.service;

import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 租户数据主权导出（SaaS 迁出）：把某租户核心业务表打包成 ZIP（每表一个 CSV）。
 * 【平台端】tenantId 由参数传入（非上下文），跨租户明细查询在 {@link TenantContext#runIgnoreTenant} 下
 * 用 JdbcTemplate 按 tenant_id 显式过滤（绕开 MyBatis 租户改写），每表 LIMIT 50000 防内存爆。
 * CSV：UTF-8 BOM + 表头 + 转义字段（Excel 友好）；ZIP 用 java.util.zip，不引新依赖。
 */
@Service
@RequiredArgsConstructor
public class TenantExportService {

    private final JdbcTemplate jdbcTemplate;

    private static final int LIMIT = 50000;

    /** 逻辑表名（CSV 文件名，不含扩展名） -> 候选物理表（首个存在的生效，兼容 md_customer→crm_customer）。 */
    private static final Map<String, List<String>> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("exp_bill", List.of("exp_bill"));
        TABLES.put("exp_bill_line", List.of("exp_bill_line"));
        TABLES.put("inv_invoice", List.of("inv_invoice"));
        TABLES.put("op_voucher", List.of("op_voucher"));
        TABLES.put("op_voucher_line", List.of("op_voucher_line"));
        TABLES.put("rc_receivable", List.of("rc_receivable"));
        TABLES.put("md_supplier", List.of("md_supplier"));
        TABLES.put("md_customer", List.of("md_customer", "crm_customer"));
        TABLES.put("sys_user", List.of("sys_user"));
        TABLES.put("wf_instance", List.of("wf_instance"));
    }

    /** 同步导出该租户核心业务表为 ZIP 字节。 */
    public byte[] export(Long tenantId) {
        final byte[][] holder = new byte[1][];
        TenantContext.runIgnoreTenant(() -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, List<String>> e : TABLES.entrySet()) {
                    String table = firstExisting(e.getValue());
                    if (table == null) {
                        continue;
                    }
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            "select * from `" + table + "` where tenant_id = ? limit " + LIMIT, tenantId);
                    zos.putNextEntry(new ZipEntry(e.getKey() + ".csv"));
                    zos.write(toCsv(rows));
                    zos.closeEntry();
                }
            } catch (Exception ex) {
                throw new RuntimeException("租户数据导出失败: " + ex.getMessage(), ex);
            }
            holder[0] = bos.toByteArray();
        });
        return holder[0];
    }

    // 工具

    /** 候选表中第一个真实存在的（大小写不敏感，按 information_schema）。 */
    private String firstExisting(List<String> candidates) {
        for (String name : candidates) {
            Integer c = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name = ?",
                    Integer.class, name);
            if (c != null && c > 0) {
                return name;
            }
        }
        return null;
    }

    /** List<Map> -> UTF-8 BOM CSV（表头取首行列名；空表只写 BOM）。 */
    private byte[] toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        if (rows.isEmpty()) {
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        List<String> cols = List.copyOf(rows.get(0).keySet());
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(cols.get(i)));
        }
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(quote(str(row.get(cols.get(i)))));
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String str(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toString().replace('T', ' ');
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString().replace('T', ' ');
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        if (v instanceof byte[]) {
            return ""; // 二进制列不外导
        }
        return String.valueOf(v);
    }

    private static String quote(String v) {
        if (v == null) {
            return "";
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
