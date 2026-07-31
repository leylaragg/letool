package com.github.leyland.letool.net.tcp;

import com.github.leyland.letool.net.protocol.PayloadCodec;
import com.github.leyland.letool.net.protocol.PayloadCodecs;

/**
 * 基于共享 {@link NetRuntime} 创建 TCP 客户端的工厂。
 */
public final class TcpClientFactory {

    /** 所有客户端共享的 Netty 线程运行时。 */
    private final NetRuntime runtime;

    /**
     * 创建客户端工厂。
     *
     * @param runtime 共享网络运行时
     */
    public TcpClientFactory(NetRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime 不能为空");
        }
        this.runtime = runtime;
    }

    /**
     * 创建字节数组请求响应客户端。
     *
     * @param options 客户端配置
     * @return 异步字节数组客户端
     */
    public TcpClient<byte[], byte[]> create(TcpClientOptions options) {
        return create(options, PayloadCodecs.bytes());
    }

    /**
     * 创建使用自定义载荷编解码器的异步客户端。
     *
     * @param options 客户端配置
     * @param payloadCodec 线程安全的载荷编解码器
     * @param <REQ> 请求对象类型
     * @param <RESP> 响应对象类型
     * @return 异步 TCP 客户端
     */
    public <REQ, RESP> TcpClient<REQ, RESP> create(
            TcpClientOptions options,
            PayloadCodec<REQ, RESP> payloadCodec) {
        return new DefaultTcpClient<>(runtime, options, payloadCodec);
    }

    /**
     * 创建字节数组同步便捷客户端。
     *
     * @param options 客户端配置
     * @return 同步字节数组客户端
     */
    public BlockingTcpClient<byte[], byte[]> createBlocking(TcpClientOptions options) {
        return createBlocking(options, PayloadCodecs.bytes());
    }

    /**
     * 创建使用自定义载荷编解码器的同步便捷客户端。
     *
     * @param options 客户端配置
     * @param payloadCodec 线程安全的载荷编解码器
     * @param <REQ> 请求对象类型
     * @param <RESP> 响应对象类型
     * @return 同步 TCP 客户端
     */
    public <REQ, RESP> BlockingTcpClient<REQ, RESP> createBlocking(
            TcpClientOptions options,
            PayloadCodec<REQ, RESP> payloadCodec) {
        TcpClient<REQ, RESP> client = create(options, payloadCodec);
        return new BlockingTcpClient<>(client, runtime);
    }

    /**
     * 获取工厂共享的网络运行时。
     *
     * @return 网络运行时
     */
    public NetRuntime runtime() {
        return runtime;
    }
}
