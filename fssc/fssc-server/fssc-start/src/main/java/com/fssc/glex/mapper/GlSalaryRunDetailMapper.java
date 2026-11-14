package com.fssc.glex.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.glex.entity.GlSalaryRunDetail;

/**
 * 薪酬发放人员级明细 Mapper（gl_salary_run_detail）。
 *
 * <p>（171）：本表建表 DDL 以<b>注释态</b>交付在
 * {@link GlSalaryRunDetail} 类注释里，产品确认执行前表不存在，
 * 故所有调用方必须 fail-soft（见 {@code GlSalaryRunService.listDetailOfRun}），
 * 不得让"表还没建"变成界面 500 或薪酬核算失败。</p>
 */
public interface GlSalaryRunDetailMapper extends BaseMapper<GlSalaryRunDetail> {
}
