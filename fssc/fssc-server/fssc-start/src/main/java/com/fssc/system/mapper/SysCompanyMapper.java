package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysCompany;
import org.apache.ibatis.annotations.Mapper;

/** 公司（纳税主体） */
@Mapper
public interface SysCompanyMapper extends BaseMapper<SysCompany> {

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `sys_company` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
