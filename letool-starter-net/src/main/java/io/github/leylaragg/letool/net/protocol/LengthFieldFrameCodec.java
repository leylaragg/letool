package io.github.leylaragg.letool.net.protocol;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.nio.ByteOrder;

/**
 * 以报文头部长度字段标识业务报文边界的分帧器。
 *
 * <p>长度字段位于报文开头，记录业务载荷长度，解码后不会向上层暴露长度字段。
 * 内置实现支持 1、2、3、4、8 字节长度字段；通用自定义报文头可通过实现
 * {@link FrameCodec} 完成。</p>
 */
public final class LengthFieldFrameCodec implements FrameCodec {

    private static final String DECODER_NAME = "letoolLengthFieldDecoder";
    private static final String ENCODER_NAME = "letoolLengthFieldEncoder";

    /** 长度字段占用的字节数。 */
    private final int lengthFieldLength;

    /** 长度字段使用的字节序。 */
    private final ByteOrder byteOrder;

    /**
     * 创建长度字段分帧器。
     *
     * @param lengthFieldLength 长度字段字节数，仅支持 1、2、3、4、8
     * @param byteOrder 长度字段字节序
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    public LengthFieldFrameCodec(int lengthFieldLength, ByteOrder byteOrder) {
        if (lengthFieldLength != 1
                && lengthFieldLength != 2
                && lengthFieldLength != 3
                && lengthFieldLength != 4
                && lengthFieldLength != 8) {
            throw new IllegalArgumentException("lengthFieldLength 仅支持 1、2、3、4、8");
        }
        if (byteOrder == null) {
            throw new IllegalArgumentException("byteOrder 不能为空");
        }
        this.lengthFieldLength = lengthFieldLength;
        this.byteOrder = byteOrder;
    }

    /**
     * 创建常用的四字节大端长度字段分帧器。
     *
     * @return 四字节大端长度字段分帧器
     */
    public static LengthFieldFrameCodec int32() {
        return new LengthFieldFrameCodec(Integer.BYTES, ByteOrder.BIG_ENDIAN);
    }

    /**
     * 创建两字节大端长度字段分帧器。
     *
     * @return 两字节大端长度字段分帧器
     */
    public static LengthFieldFrameCodec int16() {
        return new LengthFieldFrameCodec(Short.BYTES, ByteOrder.BIG_ENDIAN);
    }

    /**
     * 校验最大报文长度能够被长度字段表达，并为报文头保留整数空间。
     *
     * @param maxFrameLength 最大业务报文长度
     * @throws IllegalArgumentException 长度超出协议能力时抛出
     */
    @Override
    public void validateMaxFrameLength(int maxFrameLength) {
        FrameCodec.super.validateMaxFrameLength(maxFrameLength);
        long fieldMaximum = lengthFieldLength >= Integer.BYTES
                ? Integer.MAX_VALUE
                : (1L << (lengthFieldLength * Byte.SIZE)) - 1;
        long decoderMaximum = Integer.MAX_VALUE - (long) lengthFieldLength;
        long supportedMaximum = Math.min(fieldMaximum, decoderMaximum);
        if (maxFrameLength > supportedMaximum) {
            throw new IllegalArgumentException(
                    "maxFrameLength 超出 " + lengthFieldLength
                            + " 字节长度字段可表达范围：" + supportedMaximum);
        }
    }

    /**
     * 向流水线安装 Netty 原生长度字段编解码器。
     *
     * @param pipeline 当前连接的通道流水线
     * @param maxFrameLength 最大业务报文长度
     */
    @Override
    public void configure(ChannelPipeline pipeline, int maxFrameLength) {
        requirePipelineAndLength(pipeline, maxFrameLength);
        int maxWireFrameLength = Math.addExact(maxFrameLength, lengthFieldLength);
        pipeline.addLast(
                DECODER_NAME,
                new LengthFieldBasedFrameDecoder(
                        byteOrder,
                        maxWireFrameLength,
                        0,
                        lengthFieldLength,
                        0,
                        lengthFieldLength,
                        true));
        pipeline.addLast(
                ENCODER_NAME,
                new LengthFieldPrepender(byteOrder, lengthFieldLength, 0, false));
    }

    /**
     * 校验流水线和最大报文长度。
     *
     * @param pipeline 当前流水线
     * @param maxFrameLength 最大业务报文长度
     */
    private void requirePipelineAndLength(ChannelPipeline pipeline, int maxFrameLength) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline 不能为空");
        }
        validateMaxFrameLength(maxFrameLength);
    }
}
