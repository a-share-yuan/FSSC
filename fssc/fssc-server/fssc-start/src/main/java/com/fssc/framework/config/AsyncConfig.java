// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置：为操作审计日志写入数据库提供专用线程池，把 INSERT 从请求线程剥离出去，
 * 削平高并发下的写库热点（原 {@code OperLogAspect} 在请求线程内同步写 sys_oper_log/pf_oper_log）。
 *
 * <p>拒绝策略用 {@link ThreadPoolExecutor.CallerRunsPolicy}：队列满时回退到调用线程同步执行，
 * 宁可慢一点也不丢审计日志（审计不可缺失）。线程数刻意收小（核心2最大4），日志写库属 IO 轻量任务。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /** 操作日志专用执行器 bean 名，供 {@code @Async("operLogExecutor")} 引用。 */
    public static final String OPER_LOG_EXECUTOR = "operLogExecutor";

    /** 流程投影发件箱消费专用执行器 bean 名，供 {@code WfOutboxConsumerJob} 引用。 */
    public static final String WF_OUTBOX_EXECUTOR = "wfOutboxExecutor";

    /**
     * Flowable 引擎按 @Qualifier("applicationTaskExecutor") 强依赖此名的 AsyncTaskExecutor；
     * 一旦容器存在自定义 Executor bean，Spring Boot 的 TaskExecutionAutoConfiguration 会跳过
     * 该默认 bean 的自动配置（条件 missing bean），故此处显式补回，避免启动失败。
     */
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("app-async-");
        executor.initialize();
        return executor;
    }

    @Bean(name = OPER_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("oper-log-");
        // 队列打满时由调用线程执行，保证审计日志不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待已入队的日志写完再退出
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 流程投影发件箱（④层消息队列）消费专用执行器。
     *
     * <p><b>为什么必须是独立线程池、绝不能复用 {@link #applicationTaskExecutor()}</b>：
     * Flowable 的 AsyncTaskExecutor 与引擎自身的异步作业（{@code async-executor-activate: true}）
     * 共用同一个 {@code applicationTaskExecutor}。把投影消费塞进去，等于让"消费镜像事件"与
     * "引擎推进流程"抢同一批线程——消费被拖慢则待办不出现，引擎作业被拖慢则流程卡死，
     * 两种故障互相放大且难以归因（方案 §四 4.1 路线 C 的致命问题③）。
     *
     * <p><b>为什么是单线程（core=max=1）</b>：消费者按 {@code wf_process_outbox.id ASC} 顺序领取，
     * 单线程即天然满足"同一实例的事件串行投影"（方案 §四 4.3 的执行顺序要求），
     * 无需在该表上再实现 {@code PROCESSING} 抢占态。队列容量 4 只为兜住调度抖动，
     * 配合 {@code WfOutboxConsumerJob} 的在途互斥（同一时刻只允许一批在跑）不会堆积。
     * 拒绝策略 {@link ThreadPoolExecutor.AbortPolicy}：宁可让本轮 tick 报错跳过，
     * 也不阻塞 {@code @Scheduled} 调度线程（下一轮 3 秒后自然会再领）。
     */
    @Bean(name = WF_OUTBOX_EXECUTOR)
    public ThreadPoolTaskExecutor wfOutboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("wf-outbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
