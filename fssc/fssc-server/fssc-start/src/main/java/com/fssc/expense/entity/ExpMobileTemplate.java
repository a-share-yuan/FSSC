package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 移动端单据启用清单（exp_mobile_template，带 tenant_id）——H5 提报入口的可配面。
 *
 * <p><b>为什么要有它</b>：原先「哪些单据类型能在手机上提、哪些要回电脑端」写死在前端常量
 * {@code fssc-web/src/utils/h5.js} 的 {@code H5_DOC_TYPES} 里，调整一次清单就得重新构建、
 * 重新发布一次前端包。本表把这份清单搬到库里，改配置＝改数据，不再触碰构建产物。</p>
 *
 * <p><b>与既有配置的关系（勿重复造）</b>：
 * <ul>
 *  <li>{@code exp_bill.bill_type}/{@code bill_sub_type} 是本表 {@code billType}/{@code billSubType}
 *      的取值来源，同一套码，<b>不另造枚举</b>（只有 APPLY/CLAIM/LOAN/REPAY/PAY 五型）；</li>
 *  <li>本表管「能不能在手机上提」，<b>不管</b>「谁能提」——权限仍走 {@code exp:bill:*} 那套授权，
 *      不在这里挂第二道权限校验（两道校验的真实面迟早分叉）；</li>
 *  <li>类型中文名（label）不在本表：仍由前端常量映射。两处定义同一份文案必然漂移。</li>
 * </ul>
 *
 * <p><b>{@code mobileEnabled=0} 不是"隐藏"</b>：0 的行照样下发，前端用它渲染
 * 「以下类型请在电脑端提报」明示区。从入口里静默消失比明示不可提更糟（看不见 ≠ 不存在）。</p>
 *
 * <p>{@code templateJson} 是预留列，当前零消费者：字段级移动模版（表头字段可见性/必填性）
 * 的落位，本期既无写入方也无读取方，seed 一律 NULL。在它真正接上之前不要按该列做任何分支判断。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exp_mobile_template")
public class ExpMobileTemplate extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 单据主类型：APPLY 事前申请 / CLAIM 费用报销 / LOAN 借款 / REPAY 还款 / PAY 对公付款 */
    private String billType;
    /** 单据子类型，空串＝该主型不带子型（与 exp_bill.bill_sub_type 同码） */
    private String billSubType;
    /** 1 移动端可提报 / 0 不可提报（0 仍下发，供前端明示"去电脑端"） */
    private Integer mobileEnabled;
    /** 同档位内展示顺序（升序） */
    private Integer sort;
    /** 【预留列·当前零消费者】字段级移动模版 JSON；本期无写入方与读取方，勿按它做分支 */
    private String templateJson;
    /** 人读说明：该型在/不在移动清单的理由 */
    private String remark;
}
