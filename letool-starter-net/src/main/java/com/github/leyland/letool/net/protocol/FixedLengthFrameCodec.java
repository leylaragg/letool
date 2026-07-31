package com.github.leyland.letool.net.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * 固定长度业务报文分帧器。
 */
public final class FixedLengthFrameCodec implements FrameCodec {

    private static final String DECODER_NAME = "letoolFixedLengthDecoder";
    private static final String ENCODER_NAME = "letoolFixedLengthEncoder";

    /** 每个业务报文的固定字节数。 */
    private final int frameLength;

    /**
     * 创建定长分帧器。
     *
     * @param frameLength 每个业务报文的固定字节数
     * @throws IllegalArgumentException 长度非正数时抛出
     */
    public FixedLengthFrameCodec(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength 必须大于 0");
        }
        this.frameLength = frameLength;
    }

    /**
     * 校验最大报文长度能够容纳固定长度报文。
     *
     * @param maxFrameLength 最大业务报文长度
     * @throws IllegalArgumentException 最大长度小于固定长度时抛出
     */
    @Override
    public void validateMaxFrameLength(int maxFrameLength) {
        FrameCodec.super.validateMaxFrameLength(maxFrameLength);
        if (maxFrameLength < frameLength) {
            throw new IllegalArgumentException("maxFrameLength 不能小于固定长度");
        }
    }

    /**
     * 向流水线安装 Netty 定长解码器和出站长度校验器。
     *
     * @param pipeline 当前连接的通道流水线
     * @param maxFrameLength 最大业务报文长度
     */
    @Override
    public void configure(ChannelPipeline pipeline, int maxFrameLength) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline 不能为空");
        }
        validateMaxFrameLength(maxFrameLength);
        pipeline.addLast(DECODER_NAME, new FixedLengthFrameDecoder(frameLength));
        pipeline.addLast(ENCODER_NAME, new FixedLengthOutboundValidator(frameLength));
    }

    /**
     * 验证出站报文长度并保留缓冲区引用。
     */
    private static final class FixedLengthOutboundValidator extends MessageToMessageEncoder<ByteBuf> {

        /** 合法报文的固定字节数。 */
        private final int frameLength;

        /**
         * 创建出站长度校验器。
         *
         * @param frameLength 合法报文长度
         */
        private FixedLengthOutboundValidator(int frameLength) {
            this.frameLength = frameLength;
        }

        /**
         * 校验长度并将缓冲区传递给后续处理器。
         *
         * @param context 当前通道上下文
         * @param message 业务报文
         * @param output 编码结果集合
         */
        @Override
        protected void encode(ChannelHandlerContext context, ByteBuf message, List<Object> output) {
            if (message.readableBytes() != frameLength) {
                throw new IllegalArgumentException(
                        "固定长度报文必须为 " + frameLength + " 字节，实际为 "
                                + message.readableBytes() + " 字节");
            }
            output.add(message.retain());
        }
    }
}
