package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfTenantPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户套餐订购（pf_tenant_package）。
 * ref_tenant_id 是业务外键（不参与租户改写），查询需显式带条件。
 */
@Mapper
public interface PfTenantPackageMapper extends BaseMapper<PfTenantPackage> {

    /** 租户当前生效套餐绑定的功能码（用于登录时装载 features） */
    @Select("select distinct pf.feature_code from pf_package_feature pf " +
            "join pf_tenant_package tp on pf.package_id = tp.package_id " +
            "where tp.ref_tenant_id = #{tenantId} and tp.status = 'ACTIVE' " +
            "and pf.enabled = 1 and tp.start_time <= now() and tp.end_time >= now()")
    List<String> selectEnabledFeatures(@Param("tenantId") Long tenantId);
}
