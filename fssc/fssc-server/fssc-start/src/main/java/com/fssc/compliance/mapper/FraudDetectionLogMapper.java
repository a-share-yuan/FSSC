package com.fssc.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.compliance.entity.FraudDetectionLog;
import com.fssc.compliance.vo.FraudDetectionLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FraudDetectionLogMapper extends BaseMapper<FraudDetectionLog> {

    /**
     * 分页查询反欺诈日志（补充员工姓名和单据编号）
     */
    @Select("SELECT fdl.*, " +
            "su.nick_name AS employeeName, " +
            "eb.bill_no AS billNo " +
            "FROM fraud_detection_log fdl " +
            "LEFT JOIN sys_user su ON fdl.employee_id = su.id " +
            "LEFT JOIN exp_bill eb ON fdl.bill_id = eb.id " +
            "${ew.customSqlSegment}")
    IPage<FraudDetectionLogVO> selectPageWithRelations(Page<FraudDetectionLogVO> page,
                                                        @org.apache.ibatis.annotations.Param("ew") 
                                                        com.baomidou.mybatisplus.core.conditions.Wrapper<FraudDetectionLog> wrapper);
}
