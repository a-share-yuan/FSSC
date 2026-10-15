package com.fssc.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.collab.entity.CollabSyncLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollabSyncLogMapper extends BaseMapper<CollabSyncLog> {
}
