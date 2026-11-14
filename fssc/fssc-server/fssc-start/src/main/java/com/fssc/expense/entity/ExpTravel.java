package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 差旅明细（exp_travel，带 tenant_id）。无 create_by/update_by，独立实体。 */
@Data
@TableName("exp_travel")
public class ExpTravel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private Integer lineNo;
    private Long travelerId;
    private String travelerName;
    private Long fromCityId;
    private Long toCityId;
    /** 目的地国家/地区（：非空且≠中国=出国，触发境外标准与补贴按天核算） */
    private String destCountry;
    private LocalDate beginDate;
    private LocalDate endDate;
    /** 天数 */
    private BigDecimal days;
    /** 是否往返：1=往返 0=单程 NULL＝历史行未记录。界面「是否往返」栏参与住宿天数规则（PreApply.calcTravel 含往返 +1 天） */
    private Boolean roundTrip;
    /** 交通方式：FLIGHT/TRAIN/CAR/TAXI */
    private String trafficType;
    /** 舱位/等级 */
    private String trafficClass;
    private BigDecimal hotelAmount;
    private BigDecimal trafficAmount;
    private BigDecimal allowanceAmount;
    /** 市内交通费：表单与「小计」一直带着这一栏（PreApply.vue 绑 row.localTrafficAmount），
     *  表与实体都没有 ⇒ 分量相加小于 total_amount、重开草稿小计自己变小。 */
    private BigDecimal localTrafficAmount;
    private BigDecimal otherAmount;
    private BigDecimal totalAmount;
    /** 补贴超标并入个税（⑤）：1=超标部分需并入工资薪金计税 */
    private Integer allowanceTaxable;
    /** 1 超标准 */
    private Integer overStd;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
