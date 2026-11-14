package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNumberRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 单据编号规则（带 tenant_id） */
@Mapper
public interface SysNumberRuleMapper extends BaseMapper<SysNumberRule> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @Delete("DELETE FROM `sys_number_rule` WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}
