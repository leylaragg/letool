package io.github.leylaragg.letool.net.tcp;

import io.netty.channel.ChannelPipeline;

/**
 * 面向特殊协议的 Netty 流水线扩展函数。
 *
 * <p>扩展器的具体安装位置由 {@link TcpClientOptions} 的配置方法决定。用户处理器应
 * 遵守 Netty 引用计数和事件线程非阻塞约束。</p>
 */
@FunctionalInterface
public interface ChannelPipelineCustomizer {

    /** 不修改流水线的默认扩展器。 */
    ChannelPipelineCustomizer NONE = pipeline -> {
        // 默认不添加额外处理器。
    };

    /**
     * 为一条新连接定制流水线。
     *
     * @param pipeline 当前连接流水线
     */
    void customize(ChannelPipeline pipeline);
}
