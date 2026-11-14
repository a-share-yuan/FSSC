package com.fssc.common.core;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应。total 是【本租户】数据总量（多租户拦截器已强制 tenant_id 过滤，
 * 绝不会是全库总量 —— 静态/在线双层校验守着的红线）。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;
    /** 总条数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 当前页 */
    private long pageNum;
    /** 每页条数 */
    private long pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = total;
        r.pageNum = pageNum;
        r.pageSize = pageSize;
        r.pages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return r;
    }
}
