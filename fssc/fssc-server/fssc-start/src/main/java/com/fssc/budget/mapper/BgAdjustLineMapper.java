package com.fssc.budget.mapper;

import com.fssc.budget.entity.BgAdjustLine;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 预算调整明细（bg_adjust_line）。只增不改，无 del_flag。 */
@Mapper
public interface BgAdjustLineMapper {

    @Insert("insert into bg_adjust_line(tenant_id, adjust_id, budget_item_id, out_item_id, amount, remark, create_time) " +
            "values(#{tenantId}, #{adjustId}, #{budgetItemId}, #{outItemId}, #{amount}, #{remark}, #{createTime})")
    int insert(BgAdjustLine line);

    /** 调整单的行（租户条件由拦截器注入） */
    @Select("select * from bg_adjust_line where adjust_id = #{adjustId}")
    List<BgAdjustLine> selectByAdjustId(@Param("adjustId") Long adjustId);

    /** 删除调整单全部明细行（仅草稿编辑时使用，租户条件由拦截器注入） */
    @org.apache.ibatis.annotations.Delete("delete from bg_adjust_line where adjust_id = #{adjustId}")
    int deleteByAdjustId(@Param("adjustId") Long adjustId);
}
