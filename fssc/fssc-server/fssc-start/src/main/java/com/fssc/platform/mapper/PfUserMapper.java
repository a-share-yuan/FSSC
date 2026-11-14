package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 平台用户 */
@Mapper
public interface PfUserMapper extends BaseMapper<PfUser> {

    /** 登录查用户（pf_* 表不参与租户改写，无需 tenant 条件） */
    @Select("select * from pf_user where username = #{username} and del_flag = 0")
    PfUser findByUsername(String username);
}
