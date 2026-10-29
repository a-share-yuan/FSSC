// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpPayBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** sharing 域：OpPayBatch（带 tenant_id） */
@Mapper
public interface OpPayBatchMapper extends BaseMapper<OpPayBatch> {

    /** 行锁读取批次（付款执行/确认加锁，并发重复执行被串行化，防重复付款） */
    @Select("SELECT * FROM op_pay_batch WHERE id = #{id} FOR UPDATE")
    OpPayBatch selectForUpdate(@Param("id") Long id);
}
