// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade;

import java.util.List;
import java.util.Map;

/**
 * 影像统计 Facade（只读报告面）：报表服务独立部署时无需加载 image 域，
 * 通过 {@link RemoteImageStatsFacade} 走 HTTP 取数；单体进程内由 ImageFacadeImpl 直接实现。
 *
 * <p>只放"只读统计"两个方法：报表服务只需要它们，其余影像能力不应出现在只读服务的可见面上。</p>
 */
public interface ImageStatsFacade {

    /**
     * 统计影像批次总数（未删除；tenant_id 由租户拦截器注入）。
     */
    Long countBatches();

    /**
     * 按业务类型统计影像批次及文档数。
     *
     * @return 每行含 bizType/batchCnt/totalDocs/reviewedCnt/sealedCnt，按 batchCnt 降序
     */
    List<Map<String, Object>> statBatchByBizType();
}
