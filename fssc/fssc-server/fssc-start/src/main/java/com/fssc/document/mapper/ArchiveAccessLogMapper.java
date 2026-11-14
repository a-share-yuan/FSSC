package com.fssc.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.document.entity.ArchiveAccessLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveAccessLogMapper extends BaseMapper<ArchiveAccessLog> {
}