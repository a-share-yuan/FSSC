package com.fssc.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.image.entity.ImgBatch;
import org.apache.ibatis.annotations.Mapper;

/** 影像域：ImgBatch（带 tenant_id） */
@Mapper
public interface ImgBatchMapper extends BaseMapper<ImgBatch> {
}
