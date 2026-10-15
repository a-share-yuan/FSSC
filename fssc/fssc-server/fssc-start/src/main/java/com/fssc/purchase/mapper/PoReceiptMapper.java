package com.fssc.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.purchase.entity.PoReceipt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoReceiptMapper extends BaseMapper<PoReceipt> {

    /** 显式计数（绕过逻辑删除/包装器过滤），取号防撞唯一键用 */
    @org.apache.ibatis.annotations.Select("select count(*) from po_receipt where tenant_id = #{tid} and receipt_no = #{no}")
    int countByReceiptNo(@org.apache.ibatis.annotations.Param("tid") Long tid, @org.apache.ibatis.annotations.Param("no") String no);
}
