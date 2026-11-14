package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlSalaryRun;
import com.fssc.glex.entity.GlSalaryRunDetail;   // 工资明细行
import com.fssc.glex.service.GlSalaryRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;   // 工资明细行列表

/** GL 薪酬发放（/api/tenant/glex/salary/**） */
@RestController
@RequestMapping("/api/tenant/glex/salary")
@RequiredArgsConstructor
public class GlSalaryRunController {

    private final GlSalaryRunService service;

    @RequiresPerm("gl:salary:list")
    @GetMapping("/page")
    public Result<PageResult<GlSalaryRun>> page(PageQuery query, String period, String departmentId, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, departmentId, keyword));
    }

    /**
     * 171：某薪酬批次的人员级工资明细（应发／其他扣除／社保／个税／实发 到人）。
     *
     * <p>载体表 {@code gl_salary_run_detail} 的建表 DDL 以注释态交付、未经产品确认执行，
     * 现网无此表；本端点走 {@code listDetailOfRun} 的 fail-soft 规则，未建表时返回<b>空集</b>
     * 而非 500，界面据此显示"暂无人员级明细"。空集语义与真无明细一致：现库不存在任何
     * 到人薪酬来源，代码亦不以「人数 × 人均标准」反造明细行。</p>
     */
    @RequiresPerm("gl:salary:list")
    @GetMapping("/{id}/detail")
    public Result<List<GlSalaryRunDetail>> detail(@PathVariable Long id) {
        return Result.success(service.listDetailOfRun(id));
    }

    @RequiresPerm("gl:salary:calculate")
    @OperLog(module = "GL_SALARY", operType = "INSERT", desc = "薪酬核算")
    @PostMapping("/calculate")
    public Result<GlSalaryRun> calculate(@RequestParam String period,
                                         @RequestParam(required = false) Long deptId,
                                         @RequestParam(required = false, defaultValue = "1") Integer empCount) {
        return Result.success(service.calculateRun(period, deptId, empCount));
    }

    @RequiresPerm("gl:salary:pay")
    @OperLog(module = "GL_SALARY", operType = "UPDATE", desc = "薪酬发放")
    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        service.postPay(id);
        return Result.success();
    }
}