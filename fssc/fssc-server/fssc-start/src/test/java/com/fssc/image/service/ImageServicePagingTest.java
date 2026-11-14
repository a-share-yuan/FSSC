package com.fssc.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.paging.CountSnapshot;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgFileMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 影像任务分页的回归防线（2026-11-14 压测整改）。
 *
 * <p>核心断言：翻页不能重复 COUNT、批次清单必须带 LIMIT、封单筛选必须走 FORCE INDEX 的计数方法。
 * 这三件事失效都不会报错，只会让「台账变慢 / 一次拉 50 万行 / 索引白建」。</p>
 */
class ImageServicePagingTest {

    private ImgBatchMapper batchMapper;
    private ImageService service;

    @BeforeAll
    static void registerTableInfo() {
        // 纯单测不启动 Spring：MP 的 lambda → 列名解析依赖 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ImgBatch.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        batchMapper = mock(ImgBatchMapper.class);
        when(batchMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchMapper.selectCount(any())).thenReturn(500_002L);
        when(batchMapper.selectList(any())).thenReturn(List.of());
        service = new ImageService(batchMapper, mock(ImgFileMapper.class), null, null, null);
    }

    @AfterEach
    void tearDown() {
        CountSnapshot.clear();
        TenantContext.clear();
    }

    @Test
    void 翻三页只数一次总数() {
        PageResult<ImgBatch> p1 = service.taskPage(1, 20, null, null, null, null);
        PageResult<ImgBatch> p2 = service.taskPage(2, 20, null, null, null, null);
        PageResult<ImgBatch> p3 = service.taskPage(3, 20, null, null, null, null);

        assertEquals(500_002L, p1.getTotal());
        assertEquals(500_002L, p2.getTotal());
        assertEquals(500_002L, p3.getTotal());
        verify(batchMapper, times(1)).selectCount(any());
    }

    @Test
    void 封单状态单独筛选时走FORCE_INDEX计数() {
        when(batchMapper.countBySealStatus(eq(1L), eq("SEALED"))).thenReturn(250_000L);
        PageResult<ImgBatch> p = service.taskPage(1, 20, null, null, "SEALED", null);

        assertEquals(250_000L, p.getTotal());
        verify(batchMapper, times(1)).countBySealStatus(1L, "SEALED");
        verify(batchMapper, times(0)).selectCount(any());
    }

    @Test
    void 带业务类型时不能误用封单专用计数() {
        service.taskPage(1, 20, "CLAIM", null, "SEALED", null);
        verify(batchMapper, times(1)).selectCount(any());
        verify(batchMapper, times(0)).countBySealStatus(any(), any());
    }

    @Test
    void 批次清单必须带分页上限与偏移() {
        service.batches(null, null, "IMG", 201, 200L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ImgBatch>> cap =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(batchMapper).selectList(cap.capture());
        String seg = cap.getValue().getCustomSqlSegment().toUpperCase();
        assertTrue(seg.contains("LIMIT 201"), "无 LIMIT 会一次拉走整张影像表");
        assertTrue(seg.contains("OFFSET 200"), "翻页必须带 OFFSET");
    }

    @Test
    void 页大小超界时按上限取数() {
        assertEquals(500, service.taskPage(1, 1_000_000, null, null, null, null).getPageSize(),
                "PageQuery 没有上限校验，前端误传超大 pageSize 会拉全表");
    }
}
