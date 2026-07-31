package com.github.leyland.letool.net.tcp;

import java.util.concurrent.CompletionStage;

/**
 * 类型安全的异步 TCP 请求响应客户端。
 *
 * <p>异步接口是唯一核心语义。对于没有请求关联标识的协议，框架保证每条连接同一时间
 * 只处理一个请求，避免并发响应被错误匹配。</p>
 *
 * @param <REQ> 请求对象类型
 * @param <RESP> 响应对象类型
 */
public interface TcpClient<REQ, RESP> extends AutoCloseable {

    /**
     * 异步发送请求并等待一个完整响应。
     *
     * @param request 请求对象
     * @return 最终响应或结构化网络异常
     */
    CompletionStage<RESP> request(REQ request);

    /**
     * 获取创建客户端时使用的不可变配置。
     *
     * @return 客户端配置
     */
    TcpClientOptions options();

    /**
     * 判断客户端是否已经关闭。
     *
     * @return 已关闭时返回 {@code true}
     */
    boolean isClosed();

    /**
     * 关闭连接池和当前客户端持有的全部连接。
     */
    @Override
    void close();
}
