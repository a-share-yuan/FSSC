package com.fssc.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.platform.entity.PfNoticeTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 消息模板（平台表，无 tenant_id） */
@Mapper
public interface PfNoticeTemplateMapper extends BaseMapper<PfNoticeTemplate> {
}
