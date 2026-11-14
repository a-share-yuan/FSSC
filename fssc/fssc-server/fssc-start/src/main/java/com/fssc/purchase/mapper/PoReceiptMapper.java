package com.fssc.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.purchase.entity.PoReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PoReceiptMapper extends BaseMapper<PoReceipt> {

    /** 显式计数（绕过逻辑删除/包装器过滤），取号防撞唯一键用 */
    @Select("select count(*) from po_receipt where tenant_id = #{tid} and receipt_no = #{no}")
    int countByReceiptNo(@Param("tid") Long tid, @Param("no") String no);
}
