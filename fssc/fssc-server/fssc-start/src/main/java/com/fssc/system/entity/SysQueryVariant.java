package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询方案（Variant）——保存个人常用筛选组合。
 * <p>参考常用查询方案，每个用户可保存多套筛选方案，并设默认方案。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_query_variant")
public class SysQueryVariant extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 方案名称 */
    private String variantName;

    /** 页面标识，如 expense_bill_list */
    private String pageKey;

    /** 筛选条件JSON（searchForm 序列化） */
    private String filtersJson;

    /** 排序列 */
    private String sortBy;

    /** 排序方向 ASC/DESC */
    private String sortOrder;

    /** 是否默认方案 1=是 */
    private Integer isDefault;
}