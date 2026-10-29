// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import com.fssc.glex.service.GlSalaryRunService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 薪酬发放人员级明细（gl_salary_run_detail，带 tenant_id）。
 *
 * <p><b>库侧现状（只读查询）：</b>
 * {@code SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='fssc' AND
 * (TABLE_NAME LIKE '%salar%' OR '%payroll%' OR '%wage%' OR '%emp%')} 只有两条与薪酬沾边的表——
 * {@code gl_salary_run}（1 行）与 {@code md_employee_level}（职级字典，列只有 level_code/level_name/sort/status，
 * <b>没有员工号、没有金额</b>）；{@code employee_credit} 是员工信用与薪酬无关。
 * 再看列：{@code information_schema.COLUMNS} 里全库只有 {@code gl_salary_run.salary_amount} 与
 * {@code sys_user.emp_no} 两列命中 salar/emp_no/withhold/other_deduct——
 * 现库不存在任何人员级薪酬明细载体，也不存在可回填的行级来源。
 * 于是 {@code gl_salary_run.emp_count=10}（批次 SR202612001）无从支撑：该数由
 * {@code GlSalaryRunService.calculateRun} 的「人数 × sys_config 人均标准」算出（salary.std.basic 等 4 个参数），
 * 审批人看到的"工资"是一个总数，核不到人，代扣额也失去唯一可信来源。</p>
 *
 * <p>本类只落代码侧载体，不落数据：建表属 DDL，按本仓 114/167/028 同模式以<b>注释态</b>交付于下方
 * {@code <pre>} 块，修复批次不执行；读侧 {@code GlSalaryRunService.listDetailOfRun} 在未建表期 fail-soft
 * 只留 WARN、返回空集，不抛给界面；写侧<b>刻意不建档</b>——现库无行级来源，若按「人数 × 人均标准」
 * 生成 10 条完全相同的"人"，等于把配置参数伪造成到人证据，比现状（一个总数）更坏，故宁缺毋滥。</p>
 *
 * <pre>
 * -- 需产品确认：以下 DDL 未执行、也不得由缺陷修复批次执行（注释态交付）。
 * -- 建表后本类字段按 MP 下划线转驼峰自动对上，无需改代码；
 * -- 同时 GlSalaryRunService.checkEmpCountConsistency 的"未建表"WARN 会自动切成真实的条数比对。
 * -- CREATE TABLE gl_salary_run_detail (
 * id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 * tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
 * run_id BIGINT NOT NULL COMMENT '薪酬发放批次ID（gl_salary_run.id）',
 * run_no VARCHAR(64) NULL COMMENT '发放批次号（冗余展示）',
 * period VARCHAR(16) NULL COMMENT '会计期间 yyyyMM',
 * company_id BIGINT NULL COMMENT '记账公司（与 gl_salary_run.company_id 同一规则）',
 * dept_id BIGINT NULL COMMENT '部门 sys_dept.id',
 * employee_id BIGINT NULL COMMENT '员工ID（有薪酬台账/人事主数据后填；现库无来源，留 NULL）',
 * emp_no VARCHAR(64) NULL COMMENT '员工编号（sys_user.emp_no 同一规则；取不到留 NULL，不臆造）',
 * emp_name VARCHAR(128) NULL COMMENT '员工姓名（同上，取不到留 NULL）',
 * salary_amount DECIMAL(18,2) NULL COMMENT '本人应发工资（求和须等于 gl_salary_run.salary_amount）',
 * deduction_amount DECIMAL(18,2) NULL COMMENT '本人其他扣除（旷工/罚款等，规则＝批次 deduction_amount 的到人分项）',
 * social_security_amount DECIMAL(18,2) NULL COMMENT '本人社保个人部分（求和＝批次 social_security_amount）',
 * tax_amount DECIMAL(18,2) NULL COMMENT '本人个人所得税（求和＝批次 tax_amount）',
 * net_amount DECIMAL(18,2) NULL COMMENT '本人实发＝应发−其他扣除−社保−个税（与 assertSalaryFormula 同式）',
 * source VARCHAR(32) NULL COMMENT '明细来源 HR_LEDGER/IMPORT/MANUAL；禁止写计算反推值',
 * remark VARCHAR(500) NULL COMMENT '备注',
 * create_by BIGINT NULL COMMENT '创建人',
 * create_time DATETIME NULL COMMENT '创建时间',
 * update_by BIGINT NULL COMMENT '更新人',
 * update_time DATETIME NULL COMMENT '更新时间',
 * del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否/1是',
 * PRIMARY KEY (id),
 * UNIQUE KEY uk_gl_sal_det (tenant_id, run_id, emp_no),
 * KEY idx_gl_sal_det_run (run_id),
 * KEY idx_gl_sal_det_period (tenant_id, period)
 * -- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='薪酬发放人员级明细';
 * --
 * -- 历史回灌：现库无人员级薪酬来源，本表建好时是空表，gl_salary_run 的 3 个历史批次
 * --（SR202612001 emp_count=10／SR202609001、SR202609002 emp_count=2）不予回填——
 * -- 从"人数 × 人均标准"反推到人＝造数。历史批次长期处于 checkEmpCountConsistency 的"明细 0 行"WARN 下，
 * -- 直到接入真实薪酬台账（见下"建档写入位置前置条件"）。
 * --
 * -- 建档写入位置的前置条件（缺一条都不落写入代码，避免把参数伪装成证据）：
 * ① 上面的 DDL 经产品确认执行；
 * ② 出现行级来源——人事薪酬台账，或 md_employee 一类的员工主数据（现库没有，只有职级字典 md_employee_level）；
 * 届时在 GlSalaryRunService.calculateRun 的 insert 之后、occupyBudgetForRun 之前按人建行，
 * 并让人均标准 4 个 sys_config 参数退化为"无台账批计"兜底规则。
 * -- </pre>
 *
 * @see GlSalaryRunService#listDetailOfRun
 * @see GlSalaryRunService#checkEmpCountConsistency
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_salary_run_detail")
public class GlSalaryRunDetail extends TenantAuditBase {

    /** 薪酬发放批次ID（gl_salary_run.id） */
    private Long runId;
    /** 发放批次号（冗余展示） */
    private String runNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** 记账公司 */
    private Long companyId;
    /** 部门 sys_dept.id */
    private Long deptId;
    /** 员工ID（现库无人员级来源，取不到留 NULL） */
    private Long employeeId;
    /** 员工编号（sys_user.emp_no 同一规则；取不到留 NULL，不臆造） */
    private String empNo;
    /** 员工姓名 */
    private String empName;
    /** 本人应发工资 */
    private BigDecimal salaryAmount;
    /** 本人其他扣除（旷工/罚款等） */
    private BigDecimal deductionAmount;
    /** 本人社保个人部分 */
    private BigDecimal socialSecurityAmount;
    /** 本人个人所得税 */
    private BigDecimal taxAmount;
    /** 本人实发＝应发−其他扣除−社保−个税 */
    private BigDecimal netAmount;
    /** 明细来源 HR_LEDGER/IMPORT/MANUAL（禁写反推值） */
    private String source;
    /** 备注 */
    private String remark;
}
