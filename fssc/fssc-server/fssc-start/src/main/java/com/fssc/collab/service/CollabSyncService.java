package com.fssc.collab.service;

import com.fssc.collab.entity.CollabSyncLog;
import com.fssc.collab.mapper.CollabSyncLogMapper;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协同平台接口同步日志（蓝图异常处理范式：接口失败记日志 + 人工/定时重试）。
 * 独立成类供 apflow（关账冻结通知）与 collab（三单匹配/付款/认证/发运/回写）共用，避免双向依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollabSyncService {

    private final CollabSyncLogMapper logMapper;

    /** 记录一次接口交互。REQUIRES_NEW：日志不随主事务回滚，失败记录。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String ifaceCode, String direction, String refNo, String status, String message, String payload) {
        CollabSyncLog l = new CollabSyncLog();
        l.setTenantId(TenantContext.getTenantId());
        l.setIfaceCode(ifaceCode);
        l.setDirection(direction);
        l.setRefNo(refNo);
        l.setStatus(status);
        l.setMessage(message == null || message.length() <= 480 ? message : message.substring(0, 480));
        l.setPayload(payload == null || payload.length() <= 2000 ? payload : payload.substring(0, 2000));
        logMapper.insert(l);
        log.info("[collab] {} {} ref={} {} {}", ifaceCode, direction, refNo, status, message == null ? "" : message);
    }
}
