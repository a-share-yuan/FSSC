package com.fssc.common.core;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求基类。所有列表查询的入参都继承它。
 * 前端传 pageNum/pageSize，后端统一转换；不允许前端决定不分页（防止全表扫描泄露数据规模）。
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页，从 1 开始 */
    @Min(value = 1, message = "pageNum 必须 >= 1")
    private long pageNum = 1;

    /** 每页条数，上限由拦截器/配置控制，默认 10 */
    @Min(value = 1, message = "pageSize 必须 >= 1")
    private long pageSize = 10;

    /** 排序字段（可选），格式：create_time desc */
    private String orderBy;
}
