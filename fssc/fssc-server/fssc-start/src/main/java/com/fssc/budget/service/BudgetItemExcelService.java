// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.budget.entity.BgItem;
import com.fssc.budget.entity.BgScheme;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.budget.mapper.BgSchemeMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysDept;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysDeptMapper;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算明细 Excel 导入/导出。
 * <p>
 * 模板列（导入/导出同构）：预算年度/期间/公司编码/公司名称/部门编码/部门名称/费用项编码/费用项名称/
 * 预算总额/币种/总账科目/外部系统科目/备注。
 * 导入规则：
 * <ul>
 *   <li>方案在页面选择后随请求传入（schemeId），模板不含方案列；</li>
 *   <li>公司/部门/费用项支持「编码或名称」二选一匹配，均匹配失败则该行报错跳过；</li>
 *   <li>dimKey = 年度-COMPANY{公司id}-DEPT{部门id}-ITEM{费用项id}，与历史数据范围一致；</li>
 *   <li>按 (schemeId, dimKey) 去重 upsert：存在则更新总额等字段，不存在则新增；</li>
 *   <li>占用/核销为运行期数据，导入不触碰。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetItemExcelService {

    /** 导入/导出统一列头 */
    public static final String[] HEADERS = {
            "预算年度", "期间", "公司编码", "公司名称", "部门编码", "部门名称",
            "费用项编码", "费用项名称", "预算总额", "币种", "总账科目", "外部系统科目", "备注",
    };

    private final BgItemMapper bgItemMapper;
    private final BgSchemeMapper bgSchemeMapper;
    private final SysCompanyMapper sysCompanyMapper;
    private final SysDeptMapper sysDeptMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;

    // 导出

    /** 导出预算明细 xlsx（schemeId/deptId/itemId 可选过滤），返回字节流 */
    public byte[] exportItems(Long schemeId, Long deptId, Long itemId) {
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<>();
        if (schemeId != null) {
            qw.eq(BgItem::getSchemeId, schemeId);
        }
        if (deptId != null) {
            qw.eq(BgItem::getDeptId, deptId);
        }
        if (itemId != null) {
            qw.eq(BgItem::getItemId, itemId);
        }
        qw.orderByAsc(BgItem::getDimKey);
        List<BgItem> items = bgItemMapper.selectList(qw);

        // 名称解析缓存
        Map<Long, String> companyNames = new HashMap<>();
        Map<Long, String> companyCodes = new HashMap<>();
        Map<Long, String> deptNames = new HashMap<>();
        Map<Long, String> deptCodes = new HashMap<>();
        Map<Long, MdExpenseItem> itemMap = new HashMap<>();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("预算明细");
            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                head.createCell(c).setCellValue(HEADERS[c]);
                sheet.setColumnWidth(c, 14 * 256);
            }
            int r = 1;
            for (BgItem bi : items) {
                Row row = sheet.createRow(r++);
                SysCompany comp = bi.getCompanyId() == null ? null : sysCompanyMapper.selectById(bi.getCompanyId());
                SysDept dept = bi.getDeptId() == null ? null : sysDeptMapper.selectById(bi.getDeptId());
                MdExpenseItem mi = bi.getItemId() == null ? null : mdExpenseItemMapper.selectById(bi.getItemId());
                row.createCell(0).setCellValue(bi.getBudgetYear() == null ? "" : String.valueOf(bi.getBudgetYear()));
                row.createCell(1).setCellValue(bi.getBudgetPeriod() == null || bi.getBudgetPeriod() == 0
                        ? "全年" : String.valueOf(bi.getBudgetPeriod()));
                row.createCell(2).setCellValue(comp == null ? "" : nvl(comp.getCompanyCode()));
                row.createCell(3).setCellValue(comp == null ? "" : nvl(comp.getCompanyName()));
                row.createCell(4).setCellValue(dept == null ? "" : nvl(dept.getDeptCode()));
                row.createCell(5).setCellValue(dept == null ? "" : nvl(dept.getDeptName()));
                row.createCell(6).setCellValue(mi == null ? "" : nvl(mi.getItemCode()));
                row.createCell(7).setCellValue(mi == null ? "" : nvl(mi.getItemName()));
                row.createCell(8).setCellValue(nvl(bi.getTotalAmount()).doubleValue());
                row.createCell(9).setCellValue(bi.getCurrencyCode() == null ? "CNY" : bi.getCurrencyCode());
                row.createCell(10).setCellValue(bi.getNcAccount() == null ? "" : bi.getNcAccount());
                row.createCell(11).setCellValue(bi.getIfsAccount() == null ? "" : bi.getIfsAccount());
                row.createCell(12).setCellValue(bi.getRemark() == null ? "" : bi.getRemark());
            }
            wb.write(out);
            log.info("[budget] 预算明细导出：{} 行", items.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "预算明细导出失败：" + e.getMessage());
        }
    }

    // 导入

    public record ImportResult(int inserted, int updated, List<String> errors) {
    }

    /**
     * 导入预算明细（方案由页面选择，schemeId 必传）。
     * 解析与校验全部完成后在一个事务内 upsert，错误行收集返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importItems(Long schemeId, MultipartFile file) {
        if (schemeId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择预算方案再导入");
        }
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + schemeId);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传预算明细 Excel 文件");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = null;
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 5); i++) {
                Row r0 = sheet.getRow(i);
                if (r0 != null && "预算年度".equals(text(fmt, r0.getCell(0)))) {
                    header = r0;
                    headerRowIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未找到表头行（第一列须为「预算年度」），请使用导出文件或标准模板");
            }
            Map<String, Integer> colIdx = new LinkedHashMap<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String name = text(fmt, header.getCell(c));
                if (name != null && !name.isBlank()) {
                    colIdx.put(name, c);
                }
            }
            for (String h : new String[]{"预算年度", "期间", "费用项名称", "预算总额"}) {
                if (!colIdx.containsKey(h)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "模板缺少必需列：「" + h + "」");
                }
            }
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row r0 = sheet.getRow(i);
                if (r0 == null || isBlank(r0)) {
                    continue;
                }
                int excelRow = i + 1;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_excelRow", excelRow);
                row.put("year", num(cell(r0, colIdx, "预算年度")));
                row.put("periodText", text(fmt, cell(r0, colIdx, "期间")));
                row.put("companyCode", text(fmt, cell(r0, colIdx, "公司编码")));
                row.put("companyName", text(fmt, cell(r0, colIdx, "公司名称")));
                row.put("deptCode", text(fmt, cell(r0, colIdx, "部门编码")));
                row.put("deptName", text(fmt, cell(r0, colIdx, "部门名称")));
                row.put("itemCode", text(fmt, cell(r0, colIdx, "费用项编码")));
                row.put("itemName", text(fmt, cell(r0, colIdx, "费用项名称")));
                row.put("amount", num(cell(r0, colIdx, "预算总额")));
                row.put("currency", text(fmt, cell(r0, colIdx, "币种")));
                row.put("ncAccount", text(fmt, cell(r0, colIdx, "总账科目", "NC科目")));
                row.put("ifsAccount", text(fmt, cell(r0, colIdx, "外部系统科目", "IFS科目")));
                row.put("remark", text(fmt, cell(r0, colIdx, "备注")));
                rows.add(row);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "预算明细解析失败：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件中没有可导入的数据行");
        }

        // 主数据缓存
        Map<String, SysCompany> companyCache = new HashMap<>();
        Map<String, SysDept> deptCache = new HashMap<>();
        Map<String, MdExpenseItem> itemCache = new HashMap<>();
        int inserted = 0;
        int updated = 0;
        for (Map<String, Object> row : rows) {
            int excelRow = (Integer) row.get("_excelRow");
            BigDecimal year = (BigDecimal) row.get("year");
            BigDecimal amount = (BigDecimal) row.get("amount");
            if (year == null || year.intValue() < 2000 || year.intValue() > 2099) {
                errors.add("第" + excelRow + "行：预算年度非法，该行已跳过");
                continue;
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("第" + excelRow + "行：预算总额必填且不能为负，该行已跳过");
                continue;
            }
            int period = parsePeriod((String) row.get("periodText"));
            if (period < 0) {
                errors.add("第" + excelRow + "行：期间须为「全年」或 1-12，该行已跳过");
                continue;
            }
            MdExpenseItem mi = resolveItem(row, itemCache);
            if (mi == null) {
                errors.add("第" + excelRow + "行：费用项「" + nvl((String) row.get("itemName"))
                        + "」未匹配到主数据（支持编码或名称），该行已跳过");
                continue;
            }
            SysCompany comp = resolveCompany(row, scheme, companyCache);
            SysDept dept = resolveDept(row, comp, deptCache);
            if (dept == null && (row.get("deptCode") != null || row.get("deptName") != null)) {
                errors.add("第" + excelRow + "行：部门「" + nvl((String) row.get("deptName")) + "」未匹配到主数据，该行已跳过");
                continue;
            }
            Long companyId = comp != null ? comp.getId() : scheme.getCompanyId();
            Long deptId = dept != null ? dept.getId() : null;
            String dimKey = year.intValue() + "-COMPANY" + companyId
                    + "-DEPT" + deptId + "-ITEM" + mi.getId();
            BgItem exist = bgItemMapper.selectOne(new LambdaQueryWrapper<BgItem>()
                    .eq(BgItem::getSchemeId, schemeId)
                    .eq(BgItem::getDimKey, dimKey));
            BgItem item = exist != null ? exist : new BgItem();
            boolean isNew = exist == null;
            item.setSchemeId(schemeId);
            item.setCompanyId(companyId);
            item.setDeptId(deptId);
            item.setItemId(mi.getId());
            item.setBudgetYear(year.intValue());
            item.setBudgetPeriod(period);
            item.setDimKey(dimKey);
            item.setTotalAmount(amount);
            item.setCurrencyCode(row.get("currency") == null ? "CNY" : String.valueOf(row.get("currency")));
            item.setNcAccount((String) row.get("ncAccount"));
            item.setIfsAccount((String) row.get("ifsAccount"));
            item.setRemark((String) row.get("remark"));
            if (isNew) {
                item.setId(null);
                bgItemMapper.insert(item);
                inserted++;
            } else {
                bgItemMapper.updateById(item);
                updated++;
            }
        }
        log.info("[budget] 预算明细导入方案 {}：新增 {}，更新 {}，错误 {} 行", scheme.getSchemeCode(), inserted, updated, errors.size());
        return new ImportResult(inserted, updated, errors);
    }

    // 解析工具

    /** 期间解析：「全年」/空 → 0；1-12 → 月份；非法 → -1 */
    private int parsePeriod(String s) {
        if (s == null || s.isBlank() || "全年".equals(s.trim()) || "0".equals(s.trim())) {
            return 0;
        }
        try {
            int m = Integer.parseInt(s.trim().replace("月", ""));
            return (m >= 1 && m <= 12) ? m : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 费用项匹配：编码精确 → 名称精确（编码优先） */
    private MdExpenseItem resolveItem(Map<String, Object> row, Map<String, MdExpenseItem> cache) {
        String code = (String) row.get("itemCode");
        String name = (String) row.get("itemName");
        String key = (code == null ? "" : code) + "|" + (name == null ? "" : name);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        MdExpenseItem hit = null;
        if (code != null && !code.isBlank()) {
            hit = mdExpenseItemMapper.selectOne(new LambdaQueryWrapper<MdExpenseItem>()
                    .eq(MdExpenseItem::getItemCode, code.trim()).last("limit 1"));
        }
        if (hit == null && name != null && !name.isBlank()) {
            hit = mdExpenseItemMapper.selectOne(new LambdaQueryWrapper<MdExpenseItem>()
                    .eq(MdExpenseItem::getItemName, name.trim()).last("limit 1"));
        }
        cache.put(key, hit);
        return hit;
    }

    /** 公司匹配：编码 → 名称 → 方案所属公司 */
    private SysCompany resolveCompany(Map<String, Object> row, BgScheme scheme, Map<String, SysCompany> cache) {
        String code = (String) row.get("companyCode");
        String name = (String) row.get("companyName");
        String key = (code == null ? "" : code) + "|" + (name == null ? "" : name);
        if (cache.containsKey(key)) {
            SysCompany c = cache.get(key);
            return c != null ? c : (scheme.getCompanyId() == null ? null : sysCompanyMapper.selectById(scheme.getCompanyId()));
        }
        SysCompany hit = null;
        if (code != null && !code.isBlank()) {
            hit = sysCompanyMapper.selectOne(new LambdaQueryWrapper<SysCompany>()
                    .eq(SysCompany::getCompanyCode, code.trim()).last("limit 1"));
        }
        if (hit == null && name != null && !name.isBlank()) {
            hit = sysCompanyMapper.selectOne(new LambdaQueryWrapper<SysCompany>()
                    .eq(SysCompany::getCompanyName, name.trim()).last("limit 1"));
        }
        cache.put(key, hit);
        if (hit != null) {
            return hit;
        }
        return scheme.getCompanyId() == null ? null : sysCompanyMapper.selectById(scheme.getCompanyId());
    }

    /** 部门匹配：编码 → 名称（在公司范围内）；公司未匹配时全租户范围匹配编码 */
    private SysDept resolveDept(Map<String, Object> row, SysCompany comp, Map<String, SysDept> cache) {
        String code = (String) row.get("deptCode");
        String name = (String) row.get("deptName");
        if (code == null && name == null) {
            return null;
        }
        String scope = comp == null ? "*" : String.valueOf(comp.getId());
        String key = scope + "|" + (code == null ? "" : code) + "|" + (name == null ? "" : name);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        SysDept hit = null;
        LambdaQueryWrapper<SysDept> qw = new LambdaQueryWrapper<>();
        if (code != null && !code.isBlank()) {
            qw.eq(SysDept::getDeptCode, code.trim());
        } else {
            qw.eq(SysDept::getDeptName, name.trim());
        }
        if (comp != null) {
            qw.eq(SysDept::getCompanyId, comp.getId());
        }
        hit = sysDeptMapper.selectOne(qw.last("limit 1"));
        if (hit == null && code != null && !code.isBlank() && name != null && !name.isBlank()) {
            // 编码匹配失败再用名称兜底一次
            LambdaQueryWrapper<SysDept> qw2 = new LambdaQueryWrapper<>();
            qw2.eq(SysDept::getDeptName, name.trim());
            if (comp != null) {
                qw2.eq(SysDept::getCompanyId, comp.getId());
            }
            hit = sysDeptMapper.selectOne(qw2.last("limit 1"));
        }
        cache.put(key, hit);
        return hit;
    }

    /** 按列名取值；传入多个列名时按顺序取第一个命中的列（新旧模板表头兼容）。 */
    private Cell cell(Row r, Map<String, Integer> colIdx, String... names) {
        for (String name : names) {
            Integer idx = colIdx.get(name);
            if (idx != null) {
                return r.getCell(idx);
            }
        }
        return null;
    }

    private boolean isBlank(Row r) {
        for (int c = r.getFirstCellNum(); c < r.getLastCellNum(); c++) {
            Cell cell = r.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && cell.toString() != null && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(DataFormatter fmt, Cell cell) {
        if (cell == null) {
            return null;
        }
        String v = fmt.formatCellValue(cell).trim();
        return v.isEmpty() ? null : v;
    }

    private BigDecimal num(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String s = String.valueOf(cell).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
