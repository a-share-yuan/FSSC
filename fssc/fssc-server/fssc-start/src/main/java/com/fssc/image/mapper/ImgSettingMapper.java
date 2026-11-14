package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgSetting;
import org.apache.ibatis.annotations.Mapper;

/** 影像系统设置 */
@Mapper
public interface ImgSettingMapper extends BaseMapper<ImgSetting> {
}
