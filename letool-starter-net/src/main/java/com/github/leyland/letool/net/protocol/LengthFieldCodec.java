package com.github.leyland.letool.net.protocol;

import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.util.ByteBufUtil;

/**
 * 长度字段协议编解码器 —— 消息头部携带变长长度字段，这是最常用的自定义二进制协议格式.
 *
 * <h3>协议格式</h3>
 * <pre>
 * +------------------+--------+--------------+------------------+
 * | 头部（可选）      | 长度字段 | 调整区（可选） | 消息体            |
 * +------------------+--------+--------------+------------------+
 * | &lt;-- lengthFieldOffset --&gt;|&lt;-- lengthFieldLength --&gt;|
 * </pre>
 *
 * <h3>参数说明</h3>
 * <ul>
 *   <li>{@code lengthFieldOffset} —— 长度字段在帧中的起始位置（默认 0）</li>
 *   <li>{@code lengthFieldLength} —— 长度字段自身的字节数（默认 4）</li>
 *   <li>{@code lengthAdjustment} —— 长度字段值表示的含义需要调整的偏移量（默认 0）</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <p>典型的 TLU（Type-Length-Value）协议：</p>
 * <pre>
 * 帧格式: [2B type] [4B length] [NB body]
 * 配置: offset=2, fieldLength=4, adjustment=0
 * 则 bodyLength = 读取的4字节int值
 * </pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class LengthFieldCodec implements ProtocolCodec {

    // ======================== 字段 ========================

    /** 长度字段在帧中的起始偏移（字节），默认 0 */
    private final int lengthFieldOffset;

    /** 长度字段自身的字节数，默认 4 */
    private final int lengthFieldLength;

    /** 长度值调整量（长度字段值 + adjustment = 实际 body 长度），默认 0 */
    private final int lengthAdjustment;

    /** 最大帧长度（防止 OOM），默认 10MB */
    private final int maxFrameLength;

    // ======================== 构造器 ========================

    /**
     * 构造长度字段协议编解码器（使用默认参数）.
     */
    public LengthFieldCodec() {
        this(0, 4, 0, 10 * 1024 * 1024);
    }

    /**
     * 全参构造.
     *
     * @param lengthFieldOffset 长度字段起始偏移
     * @param lengthFieldLength 长度字段字节数
     * @param lengthAdjustment  长度值调整量
     * @param maxFrameLength    最大帧长度
     */
    public LengthFieldCodec(int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int maxFrameLength) {
        if (lengthFieldOffset < 0) {
            throw new IllegalArgumentException("lengthFieldOffset must be >= 0: " + lengthFieldOffset);
        }
        if (lengthFieldLength <= 0 || lengthFieldLength > 8) {
            throw new IllegalArgumentException("lengthFieldLength must be 1-8: " + lengthFieldLength);
        }
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        this.maxFrameLength = maxFrameLength > 0 ? maxFrameLength : 10 * 1024 * 1024;
    }

    /**
     * 简化的构造器（默认 4 字节大端长度字段）.
     *
     * @param lengthFieldOffset 长度字段起始偏移
     * @param lengthAdjustment  长度值调整量
     */
    public LengthFieldCodec(int lengthFieldOffset, int lengthAdjustment) {
        this(lengthFieldOffset, 4, lengthAdjustment, 10 * 1024 * 1024);
    }

    // ======================== encode ========================

    /**
     * 将消息编码为长度字段协议帧.
     *
     * <p>编码格式：</p>
     * <pre>
     * [填充区 offset 字节] [4B 大端长度] [body 字节]
     * </pre>
     *
     * @param message 待编码的消息对象（会调用 toString 转为字符串，再转 UTF-8 字节）
     * @return 完整的协议帧字节数组
     * @throws NetException 如果消息为 {@code null}
     */
    @Override
    public byte[] encode(Object message) {
        if (message == null) {
            throw new NetException("Cannot encode null message with LengthFieldCodec");
        }
        byte[] body = message.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int bodyLength = body.length;
        // 头部长度 = offset + fieldLength
        int headerLength = lengthFieldOffset + lengthFieldLength;
        byte[] frame = new byte[headerLength + body.length];

        // 写入长度字段（大端序）
        writeLength(frame, lengthFieldOffset, lengthFieldLength, bodyLength - lengthAdjustment);

        // 写入 body
        System.arraycopy(body, 0, frame, headerLength, body.length);
        return frame;
    }

    // ======================== decode ========================

    /**
     * 从字节数组中解码长度字段协议帧.
     *
     * @param bytes 原始字节数组
     * @return 解码后的 body 字符串
     * @throws NetException 如果字节数组为 {@code null}、长度不足或帧超长
     */
    @Override
    public Object decode(byte[] bytes) {
        if (bytes == null) {
            throw new NetException("Cannot decode null bytes with LengthFieldCodec");
        }
        int headerLength = lengthFieldOffset + lengthFieldLength;
        if (bytes.length < headerLength) {
            throw new NetException("Frame too short: " + bytes.length + " < " + headerLength);
        }
        int bodyLength = readLength(bytes, lengthFieldOffset, lengthFieldLength) + lengthAdjustment;
        if (bodyLength < 0) {
            throw new NetException("Invalid body length: " + bodyLength);
        }
        if (bodyLength > maxFrameLength) {
            throw new NetException("Frame exceeds maxFrameLength: " + bodyLength + " > " + maxFrameLength);
        }
        int totalLength = headerLength + bodyLength;
        if (bytes.length < totalLength) {
            throw new NetException("Frame incomplete: expected " + totalLength + " but got " + bytes.length);
        }
        byte[] body = ByteBufUtil.subArray(bytes, headerLength, bodyLength);
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ======================== getProtocolName ========================

    @Override
    public String getProtocolName() {
        return "LengthField(offset=" + lengthFieldOffset + ", len=" + lengthFieldLength
                + ", adj=" + lengthAdjustment + ")";
    }

    // ======================== 内部工具方法 ========================

    /**
     * 从字节数组中读取大端序整数.
     *
     * @param src    源字节数组
     * @param offset 长度字段起始偏移
     * @param length 长度字段字节数
     * @return 读取的整数值
     */
    private int readLength(byte[] src, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (src[offset + i] & 0xFF);
        }
        return value;
    }

    /**
     * 将整数值以大端序写入字节数组.
     *
     * @param dest   目标字节数组
     * @param offset 写入起始偏移
     * @param length 长度字段字节数
     * @param value  要写入的整数值
     */
    private void writeLength(byte[] dest, int offset, int length, int value) {
        for (int i = length - 1; i >= 0; i--) {
            dest[offset + i] = (byte) (value & 0xFF);
            value >>= 8;
        }
    }
}
