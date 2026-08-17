package io.github.leylaragg.letool.net.protocol;

import io.netty.channel.ChannelPipeline;

/**
 * TCP 报文边界处理器。
 *
 * <p>实现类只负责处理粘包和拆包，不负责业务对象序列化。每次初始化连接时都会调用
 * {@link #configure(ChannelPipeline, int)}，实现类应当为当前通道创建独立的有状态处理器。</p>
 */
@FunctionalInterface
public interface FrameCodec {

    /**
     * 在客户端构建阶段校验最大业务报文长度。
     *
     * <p>自定义实现可以覆盖该方法，提前拒绝协议字段无法表达的长度。默认只要求长度为
     * 正数。</p>
     *
     * @param maxFrameLength 最大业务报文长度
     * @throws IllegalArgumentException 长度超出协议能力时抛出
     */
    default void validateMaxFrameLength(int maxFrameLength) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength 必须大于 0");
        }
    }

    /**
     * 向通道流水线安装入站解帧器和出站组帧器。
     *
     * @param pipeline 当前连接的通道流水线
     * @param maxFrameLength 允许接收的最大业务报文长度，单位为字节
     */
    void configure(ChannelPipeline pipeline, int maxFrameLength);
}
