// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.mapper.ExpensePolicyMapper;
import com.fssc.compliance.mapper.ExpensePolicyStandardMapper;
import com.fssc.compliance.mapper.FraudDetectionLogMapper;
import com.fssc.compliance.mapper.ReviewRuleMapper;
import com.fssc.document.entity.OpArchive;
import com.fssc.framework.facade.ArchiveFacade;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 合规模块 Excel 导出。租户拦截器自动隔离；只读导出不写入数据库。 */
@Service
@RequiredArgsConstructor
public class ComplianceExportService {

    private final ReviewRuleMapper reviewRuleMapper;
    private final ExpensePolicyStandardMapper expensePolicyStandardMapper;
    private final ExpensePolicyMapper expensePolicyMapper;
    private final ArchiveFacade archiveFacade;
    private final FraudDetectionLogMapper fraudDetectionLogMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 导出预检规则列表 */
    public byte[] exportPrecheckRules() {
        List<ReviewRule> rules = reviewRuleMapper.selectList(
                new LambdaQueryWrapper<ReviewRule>().orderByAsc(ReviewRule::getId));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("预检规则");
            String[] heads = {"编号", "规则名称", "规则分类", "规则级别", "状态", "排序", "创建时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
            int r = 1;
            for (ReviewRule rule : rules) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(rule.getRuleCode()));
                row.createCell(1).setCellValue(str(rule.getRuleName()));
                row.createCell(2).setCellValue(str(rule.getRuleCategory()));
                row.createCell(3).setCellValue(str(rule.getRuleLevel()));
                row.createCell(4).setCellValue("NORMAL".equals(rule.getStatus()) ? "启用" : "停用");
                row.createCell(5).setCellValue(rule.getRuleOrder() != null ? rule.getRuleOrder().doubleValue() : 0);
                row.createCell(6).setCellValue(fmt(rule.getUpdateTime()));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出预检规则失败", e);
        }
    }

    /** 导出差旅标准列表 */
    public byte[] exportTravelStandards() {
        List<ExpensePolicyStandard> list = expensePolicyStandardMapper.selectList(
                new LambdaQueryWrapper<ExpensePolicyStandard>().orderByAsc(ExpensePolicyStandard::getId));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("差旅标准");
            String[] heads = {"员工级别", "国内住宿", "国内餐补", "国内其他", "国际住宿", "国际餐补", "国际其他", "状态", "创建时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
            int r = 1;
            for (ExpensePolicyStandard t : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(t.getEmployeeLevel()));
                row.createCell(1).setCellValue(nvl(t.getDomesticAccommodation()).doubleValue());
                row.createCell(2).setCellValue(nvl(t.getDomesticMeal()).doubleValue());
                row.createCell(3).setCellValue(nvl(t.getDomesticOther()).doubleValue());
                row.createCell(4).setCellValue(nvl(t.getInternationalAccommodation()).doubleValue());
                row.createCell(5).setCellValue(nvl(t.getInternationalMeal()).doubleValue());
                row.createCell(6).setCellValue(nvl(t.getInternationalOther()).doubleValue());
                row.createCell(7).setCellValue(t.getStdStatus() != null && t.getStdStatus() == 1 ? "启用" : "停用");
                row.createCell(8).setCellValue(fmt(t.getCreateTime()));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出差旅标准失败", e);
        }
    }

    /** 导出费用政策列表 */
    public byte[] exportExpensePolicies() {
        List<ExpensePolicy> list = expensePolicyMapper.selectList(
                new LambdaQueryWrapper<ExpensePolicy>().orderByAsc(ExpensePolicy::getId));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("费用政策");
            String[] heads = {"政策名称", "费用类型", "开始日期", "结束日期", "状态", "创建时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
            int r = 1;
            for (ExpensePolicy p : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(p.getPolicyName()));
                row.createCell(1).setCellValue(str(p.getExpenseType()));
                row.createCell(2).setCellValue(p.getEffectiveStart() == null ? "" : p.getEffectiveStart().toString());
                row.createCell(3).setCellValue(p.getEffectiveEnd() == null ? "" : p.getEffectiveEnd().toString());
                row.createCell(4).setCellValue(p.getStatus() != null && p.getStatus() == 1 ? "启用" : "停用");
                row.createCell(5).setCellValue(fmt(p.getCreateTime()));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出费用政策失败", e);
        }
    }

    /** 导出档案列表 */
    public byte[] exportArchives() {
        List<OpArchive> list = archiveFacade.findAllForExport();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("档案列表");
            String[] heads = {"档案编码", "档案类型", "档案年份", "档案期间", "保留期限", "状态", "创建时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
            int r = 1;
            for (OpArchive a : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(str(a.getArchiveCode()));
                row.createCell(1).setCellValue(str(a.getArchiveType()));
                row.createCell(2).setCellValue(a.getArchiveYear() != null ? a.getArchiveYear().doubleValue() : 0);
                row.createCell(3).setCellValue(str(a.getArchivePeriod()));
                row.createCell(4).setCellValue(a.getRetentionYears() != null ? a.getRetentionYears().doubleValue() : 0);
                row.createCell(5).setCellValue(str(a.getArchiveStatus()));
                row.createCell(6).setCellValue(fmt(a.getCreateTime()));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出档案列表失败", e);
        }
    }

    /** 导出欺诈检测记录 */
    public byte[] exportFraudLogs() {
        List<FraudDetectionLog> list = fraudDetectionLogMapper.selectList(
                new LambdaQueryWrapper<FraudDetectionLog>().orderByDesc(FraudDetectionLog::getId));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("欺诈检测记录");
            String[] heads = {"员工ID", "单据ID", "检测类型", "检测结果", "风险评分", "处理", "发现时间"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
            int r = 1;
            for (FraudDetectionLog log : list) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(log.getEmployeeId() != null ? log.getEmployeeId().doubleValue() : 0);
                row.createCell(1).setCellValue(log.getBillId() != null ? log.getBillId().doubleValue() : 0);
                row.createCell(2).setCellValue(str(log.getDetectionType()));
                row.createCell(3).setCellValue(str(log.getDetectionResult()));
                row.createCell(4).setCellValue(nvl(log.getRiskScore()).doubleValue());
                row.createCell(5).setCellValue(str(log.getActionTaken()));
                row.createCell(6).setCellValue(fmt(log.getCreateTime()));
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("导出欺诈检测记录失败", e);
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}