package com.fssc.bank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.bank.entity.BankReconItem;
import org.apache.ibatis.annotations.Mapper;

/** 银企未达账差异明细 Mapper（带 tenant_id） */
@Mapper
public interface BankReconItemMapper extends BaseMapper<BankReconItem> {

    /** 物理删除指定账户+月的全部明细（重建当月明细用，绕过 @TableLogic） */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM `bank_recon_item` WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND month=#{month}")
    int hardDeleteByMonth(@org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                          @org.apache.ibatis.annotations.Param("accountId") Long accountId,
                          @org.apache.ibatis.annotations.Param("month") String month);
}