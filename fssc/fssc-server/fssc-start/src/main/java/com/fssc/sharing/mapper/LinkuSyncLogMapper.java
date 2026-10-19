package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.LinkuSyncLog;
import org.apache.ibatis.annotations.Mapper;

/** LinkU 回调同步日志 Mapper */
@Mapper
public interface LinkuSyncLogMapper extends BaseMapper<LinkuSyncLog> {
}
