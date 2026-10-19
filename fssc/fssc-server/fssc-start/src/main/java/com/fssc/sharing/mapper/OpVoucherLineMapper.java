package com.fssc.sharing.mapper;

import com.fssc.sharing.entity.OpVoucherLine;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 凭证分录（带 tenant_id）。自定义 @Insert 必须显式 setTenantId。 */
@Mapper
public interface OpVoucherLineMapper {

    @Insert("insert into op_voucher_line(tenant_id, voucher_id, line_no, summary, subject_id, subject_code, " +
            "subject_name, direction, amount, currency_code, exchange_rate, orig_amount, dept_id, project_id, supplier_id, create_time) " +
            "values(#{tenantId}, #{voucherId}, #{lineNo}, #{summary}, #{subjectId}, #{subjectCode}, " +
            "#{subjectName}, #{direction}, #{amount}, #{currencyCode}, #{exchangeRate}, #{origAmount}, " +
            "#{deptId}, #{projectId}, #{supplierId}, #{createTime})")
    int insert(OpVoucherLine line);

    @Select("select * from op_voucher_line where voucher_id = #{voucherId} order by line_no")
    List<OpVoucherLine> selectByVoucherId(@Param("voucherId") Long voucherId);

    @Delete("delete from op_voucher_line where voucher_id = #{voucherId}")
    int deleteByVoucherId(@Param("voucherId") Long voucherId);
}
