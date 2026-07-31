package com.github.leyland.letool.net.tcp;

/**
 * TCP 客户端底层连接复用模式。
 */
public enum ConnectionMode {

    /**
     * 每次请求创建一条连接，收到响应或失败后立即关闭。
     */
    SHORT,

    /**
     * 复用单条持久连接，请求按连接独占模型串行执行。
     */
    PERSISTENT,

    /**
     * 使用有界固定连接池，每条连接同一时间只处理一个请求。
     */
    POOLED
}
