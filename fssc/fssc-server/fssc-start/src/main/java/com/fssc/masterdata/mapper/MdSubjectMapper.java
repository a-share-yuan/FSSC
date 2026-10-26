// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdSubject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 主数据：MdSubject（带 tenant_id，租户拦截器自动隔离） */
@Mapper
public interface MdSubjectMapper extends BaseMapper<MdSubject> {

    /** R5-D-158 写侧守卫：该科目下的存活下级科目行数（有下级即不得当作末级科目）。 */
    @Select("select count(1) from md_subject where parent_id = #{subjectId} and del_flag = 0")
    long countChildren(@Param("subjectId") Long subjectId);

    /** R5-D-158 写侧守卫：该科目被凭证分录引用的行数（op_voucher_line 无软删列，全量计数）。 */
    @Select("select count(1) from op_voucher_line where subject_id = #{subjectId}")
    long countVoucherLines(@Param("subjectId") Long subjectId);

    /** R5-D-158 写侧守卫：该科目被总账分录引用的行数（op_gl_entry 无软删列，全量计数）。 */
    @Select("select count(1) from op_gl_entry where subject_id = #{subjectId}")
    long countGlEntries(@Param("subjectId") Long subjectId);
}
