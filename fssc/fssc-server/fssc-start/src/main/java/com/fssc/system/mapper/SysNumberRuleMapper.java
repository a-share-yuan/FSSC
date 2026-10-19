package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNumberRule;
import org.apache.ibatis.annotations.Mapper;

/** 单据编号规则（带 tenant_id） */
@Mapper
public interface SysNumberRuleMapper extends BaseMapper<SysNumberRule> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `sys_number_rule` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
