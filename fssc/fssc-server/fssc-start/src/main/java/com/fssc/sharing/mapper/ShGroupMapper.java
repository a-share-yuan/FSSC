package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.ShGroup;
import org.apache.ibatis.annotations.Mapper;

/** 共享业务分组（.2：分组管理，按业务类型/区域划分，负责人+成员数上下限+任务提取方式） */
@Mapper
public interface ShGroupMapper extends BaseMapper<ShGroup> {
}
