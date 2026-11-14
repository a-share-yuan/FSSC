package com.fssc.sharing.mapper;

import com.fssc.sharing.entity.OpVoucherLine;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
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

    /**
     * 批量取分录（消除 N+1）。
     *
     * <p>单据全链路联查此前逐张凭证调 {@link #selectByVoucherId}，打在 153 万行的
     * {@code op_voucher_line} 上；本方法一次 {@code IN} 取回，走
     * {@code idx_tenant_voucher(tenant_id, voucher_id)}。租户条件由租户拦截器统一追加，
     * 与单张查询口径一致。</p>
     *
     * <p>空集合直接返回空列表（不拼出非法的 {@code in ()}）。</p>
     */
    @Select("<script>"
            + "select * from op_voucher_line where voucher_id in "
            + "<foreach item='vid' collection='voucherIds' open='(' separator=',' close=')'>#{vid}</foreach> "
            + "order by voucher_id, line_no"
            + "</script>")
    List<OpVoucherLine> selectByVoucherIds(@Param("voucherIds") Collection<Long> voucherIds);

    @Delete("delete from op_voucher_line where voucher_id = #{voucherId}")
    int deleteByVoucherId(@Param("voucherId") Long voucherId);
}
