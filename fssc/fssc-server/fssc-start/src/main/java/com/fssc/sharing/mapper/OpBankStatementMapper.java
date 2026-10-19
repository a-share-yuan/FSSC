package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpBankStatement;
import org.apache.ibatis.annotations.Mapper;

/** 银行流水/回单（带 tenant_id） */
@Mapper
public interface OpBankStatementMapper extends BaseMapper<OpBankStatement> {
}
