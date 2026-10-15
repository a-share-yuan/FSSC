package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.OpAuditTask;
import org.apache.ibatis.annotations.Mapper;

/** 共享运营派工单（带 tenant_id） */
@Mapper
public interface OpAuditTaskMapper extends BaseMapper<OpAuditTask> {
}
