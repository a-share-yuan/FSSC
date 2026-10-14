// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.budget.entity.BgItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 预算明细（occupy/used 为汇总值）。
 * P1-1 并发控制：selectForUpdate 行锁串行化同一科目；addXxx 原子 UPDATE 防丢失更新（读-改-写）。
 */
@Mapper
public interface BgItemMapper extends BaseMapper<BgItem> {

    /** 行锁读取（占用/核销/扣减写路径先锁行，防止并发超占） */
    @Select("SELECT * FROM bg_item WHERE id = #{id} FOR UPDATE")
    BgItem selectForUpdate(@Param("id") Long id);

    @Update("UPDATE bg_item SET occupy_amount = IFNULL(occupy_amount, 0) + #{delta} WHERE id = #{id}")
    int addOccupy(@Param("id") Long id, @Param("delta") BigDecimal delta);

    @Update("UPDATE bg_item SET used_amount = IFNULL(used_amount, 0) + #{delta} WHERE id = #{id}")
    int addUsed(@Param("id") Long id, @Param("delta") BigDecimal delta);

    @Update("UPDATE bg_item SET adjust_amount = IFNULL(adjust_amount, 0) + #{delta} WHERE id = #{id}")
    int addAdjust(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
