package com.fssc.sharing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.sharing.entity.ShCalendar;
import org.apache.ibatis.annotations.Mapper;

/** 共享工作日历（.3：工作日历，休息日/工作日/在岗时间定义） */
@Mapper
public interface ShCalendarMapper extends BaseMapper<ShCalendar> {
}
