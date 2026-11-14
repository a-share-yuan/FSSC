package com.fssc.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.ai.entity.AiActionConfirm;
import org.apache.ibatis.annotations.Mapper;

/** 确认票据：状态机推进用 update（票据非审计日志），但绝不物理删除。 */
@Mapper
public interface AiActionConfirmMapper extends BaseMapper<AiActionConfirm> {
}
