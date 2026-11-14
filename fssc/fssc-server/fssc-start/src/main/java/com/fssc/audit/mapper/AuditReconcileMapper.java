package com.fssc.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.audit.entity.AuditReconcile;
import org.apache.ibatis.annotations.Mapper;

/** 对账异常流水 Mapper：只读 + 状态更新，不提供物理删除（审计表铁律）。 */
@Mapper
public interface AuditReconcileMapper extends BaseMapper<AuditReconcile> {
}
