package com.fssc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.expense.entity.ExpBillAttach;
import org.apache.ibatis.annotations.Mapper;

/** 报销单附件（带 tenant_id） */
@Mapper
public interface ExpBillAttachMapper extends BaseMapper<ExpBillAttach> {
}
