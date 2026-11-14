package com.fssc.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.masterdata.entity.GlAccountingBook;
import org.apache.ibatis.annotations.Mapper;

/** GlAccountingBook Mapper（多账簿体系，闭环差距③）。 */
@Mapper
public interface GlAccountingBookMapper extends BaseMapper<GlAccountingBook> {
}
