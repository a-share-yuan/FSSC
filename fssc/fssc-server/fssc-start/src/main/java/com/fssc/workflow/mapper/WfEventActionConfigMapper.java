package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfEventActionConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程事件收尾动作配置 Mapper。
 */
@Mapper
public interface WfEventActionConfigMapper extends BaseMapper<WfEventActionConfig> {
}
