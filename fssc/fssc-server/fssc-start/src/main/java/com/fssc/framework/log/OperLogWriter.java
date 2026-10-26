// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.log;

import com.fssc.framework.config.AsyncConfig;
import com.fssc.platform.entity.PfOperLog;
import com.fssc.platform.mapper.PfOperLogMapper;
import com.fssc.system.entity.SysOperLog;
import com.fssc.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步写入器。
 * <p>
 * 由 {@link OperLogAspect} 在请求线程内完成上下文采集与实体组装后，把纯 INSERT 动作交给本组件，
 * 通过 {@code @Async("operLogExecutor")} 在专用线程池写入数据库。注意 {@code @Async} 需跨 bean 调用才生效，
 * 故从切面分离到独立组件。
 * <p>
 * 保持“日志失败不影响业务”语义：写库异常在本组件内 try-catch 吞掉，仅记录，不回传调用方。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogWriter {

    private final PfOperLogMapper pfOperLogMapper;
    private final SysOperLogMapper sysOperLogMapper;

    /** 异步写平台操作日志（pf_oper_log）。 */
    @Async(AsyncConfig.OPER_LOG_EXECUTOR)
    public void writePf(PfOperLog log0) {
        try {
            pfOperLogMapper.insert(log0);
        } catch (Exception ex) {
            log.error("审计日志写入失败(pf_oper_log)", ex);
        }
    }

    /** 异步写租户操作日志（sys_oper_log）。 */
    @Async(AsyncConfig.OPER_LOG_EXECUTOR)
    public void writeSys(SysOperLog log0) {
        try {
            sysOperLogMapper.insert(log0);
        } catch (Exception ex) {
            log.error("审计日志写入失败(sys_oper_log)", ex);
        }
    }
}
