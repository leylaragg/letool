package com.github.leyland.letool.net.tcp;

import java.time.Duration;

/**
 * 持久 TCP 连接的应用层心跳策略。
 *
 * <p>心跳内容和响应语义与业务协议相关，必须由用户明确提供。实现会被事件线程调用，
 * 应保持线程安全且不得执行阻塞操作。</p>
 */
public interface HeartbeatStrategy {

    /**
     * 获取连接连续无写操作多久后发送心跳。
     *
     * @return 正数空闲时长
     */
    Duration idleInterval();

    /**
     * 获取单次心跳等待应答的最大时长。
     *
     * <p>默认与写空闲间隔一致。网络波动较大的场景应根据协议和链路质量显式放宽。</p>
     *
     * @return 正数应答超时
     */
    default Duration responseTimeout() {
        return idleInterval();
    }

    /**
     * 获取连续多少个应答等待窗口未收到同一 ACK 后关闭连接。
     *
     * <p>为避免无关联 ID 协议产生多个并行心跳，框架只发送一个心跳，并以
     * {@link #responseTimeout()} 为窗口持续等待同一个 ACK。默认允许跨越三个窗口，
     * 期间连接保持隔离，不会写出业务请求。</p>
     *
     * @return 正整数最大等待窗口数
     */
    default int maxMissedResponses() {
        return 3;
    }

    /**
     * 创建不包含报文边界头尾的心跳业务载荷。
     *
     * @return 非空心跳载荷
     */
    byte[] heartbeatPayload();

    /**
     * 判断完整入站载荷是否为心跳响应。
     *
     * @param response 完整响应载荷
     * @return 属于心跳响应时返回 {@code true}
     */
    boolean isHeartbeatResponse(byte[] response);
}
