package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysNotifyTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 消息模板（触发点 → 标题/正文/深链） */
@Mapper
public interface SysNotifyTemplateMapper extends BaseMapper<SysNotifyTemplate> {
}
