package com.github.leyland.letool.net.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 使用固定字节序列分隔业务报文的分帧器。
 */
public final class DelimiterFrameCodec implements FrameCodec {

    private static final String DECODER_NAME = "letoolDelimiterDecoder";
    private static final String ENCODER_NAME = "letoolDelimiterEncoder";

    /** 防御性复制后的报文分隔符。 */
    private final byte[] delimiter;

    /**
     * 创建分隔符分帧器。
     *
     * @param delimiter 非空分隔符
     * @throws IllegalArgumentException 分隔符为空时抛出
     */
    public DelimiterFrameCodec(byte[] delimiter) {
        if (delimiter == null || delimiter.length == 0) {
            throw new IllegalArgumentException("delimiter 不能为空");
        }
        this.delimiter = Arrays.copyOf(delimiter, delimiter.length);
    }

    /**
     * 创建换行符分帧器。
     *
     * @return 使用 {@code \n} 的分帧器
     */
    public static DelimiterFrameCodec lineFeed() {
        return new DelimiterFrameCodec("\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 向流水线安装 Netty 原生分隔符解码器和安全的出站追加器。
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
        ByteBuf decoderDelimiter = Unpooled.wrappedBuffer(Arrays.copyOf(delimiter, delimiter.length));
        pipeline.addLast(
                DECODER_NAME,
                new DelimiterBasedFrameDecoder(maxFrameLength, true, true, decoderDelimiter));
        pipeline.addLast(
                ENCODER_NAME,
                new DelimiterAppendingEncoder(delimiter));
    }

    /**
     * 出站时复制业务报文并追加分隔符，避免修改调用方持有的缓冲区。
     */
    private static final class DelimiterAppendingEncoder extends MessageToMessageEncoder<ByteBuf> {

        /** 当前连接独立持有的分隔符副本。 */
        private final byte[] delimiter;

        /**
         * 创建出站分隔符追加器。
         *
         * @param delimiter 需要追加的分隔符
         */
        private DelimiterAppendingEncoder(byte[] delimiter) {
            this.delimiter = Arrays.copyOf(delimiter, delimiter.length);
        }

        /**
         * 复制业务报文并追加分隔符。
         *
         * @param context 当前通道上下文
         * @param message 业务报文
         * @param output 编码结果集合
         */
        @Override
        protected void encode(ChannelHandlerContext context, ByteBuf message, List<Object> output) {
            ByteBuf framed = context.alloc().buffer(message.readableBytes() + delimiter.length);
            framed.writeBytes(message, message.readerIndex(), message.readableBytes());
            framed.writeBytes(delimiter);
            output.add(framed);
        }
    }
}
