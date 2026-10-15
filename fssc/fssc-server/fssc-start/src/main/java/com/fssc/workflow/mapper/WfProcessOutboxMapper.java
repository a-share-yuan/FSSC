package com.fssc.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.workflow.entity.WfProcessOutbox;
import org.apache.ibatis.annotations.Mapper;

/** 流程投影事件发件箱（wf_process_outbox，带 tenant_id） */
@Mapper
public interface WfProcessOutboxMapper extends BaseMapper<WfProcessOutbox> {
}