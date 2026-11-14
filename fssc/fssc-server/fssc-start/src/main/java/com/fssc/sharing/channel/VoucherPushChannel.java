package com.fssc.sharing.channel;

import java.util.Map;

/**
 * 凭证推送通道 SPI（可插拔）。业务层（VoucherPushService）只依赖本接口，
 * 推送目标（外部财务系统）与传输方式切换不影响推送流程。
 *
 * <p>双实现按 sys_config {@code voucher.push.channel}（系统设置→全局参数）条件路由，
 * 为空回退 yml {@code fssc.voucher.push.channel}（默认 {@code local}）：</p>
 * <ul>
 *   <li>{@link LocalVoucherPushChannel}（local 或缺省）：本地占位实现，不产生任何
 *       网络副作用与人为延迟，返回确定性成功，供演示/冒烟使用；</li>
 *   <li>{@link HttpVoucherPushChannel}（http）：通用 HTTP 通道，真实对接 外部财务系统，
 *       只需配置 url/token，无需改代码。</li>
 * </ul>
 *
 * <p>约定：实现不得向调用方抛穿异常——失败以 {@link VoucherPushResult#isOk()}=false
 * + message 返回，由 VoucherPushService 写入数据库为 FAILED 状态。</p>
 */
public interface VoucherPushChannel {

    /** 通道名（local / http），与配置值一致 */
    String name();

    /**
     * 推送一张凭证至外部系统。
     *
     * @param target 推送目标（NC_SYSTEM / IFS_SYSTEM）
     * @param payload 凭证业务数据（凭证号、借贷合计、分录摘要等），供通道序列化
     * @return 推送结果，永不返回 null、永不抛穿
     */
    VoucherPushResult push(String target, Map<String, Object> payload);
}
