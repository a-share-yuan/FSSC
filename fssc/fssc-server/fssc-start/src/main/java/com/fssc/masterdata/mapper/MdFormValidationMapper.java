package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.MdFormValidation;
import org.apache.ibatis.annotations.Mapper;

/** MdFormValidation Mapper（元数据驱动）。 */
@Mapper
public interface MdFormValidationMapper extends BaseMapper<MdFormValidation> {
}
