package com.github.leyland.letool.net.protocol;

import com.github.leyland.letool.net.exception.NetException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 定长帧协议编解码器 —— 所有消息使用固定长度的字节帧传输.
 *
 * <p>编码规则：将消息字符串按指定字符集转为字节，不足则用 0x00 填充，超出则截断.</p>
 * <p>解码规则：取前 frameLength 字节去除尾部填充的 0x00 后转为字符串.</p>
 *
 * <h3>使用场景</h3>
 * <p>适用于消息长度固定的遗留系统接口（如 POS 终端、银行前置机等）.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class FixedLengthCodec implements ProtocolCodec {

    // ======================== 字段 ========================

    /** 固定帧长度（字节数） */
    private final int frameLength;

    /** 字符编码，默认 UTF-8 */
    private final Charset charset;

    // ======================== 构造器 ========================

    /**
     * 构造定长帧编解码器（使用 UTF-8 编码）.
     *
     * @param frameLength 固定帧长度（字节数），必须 &gt; 0
     * @throws IllegalArgumentException 如果 frameLength &le; 0
     */
    public FixedLengthCodec(int frameLength) {
        this(frameLength, StandardCharsets.UTF_8);
    }

    /**
     * 构造定长帧编解码器并指定字符集.
     *
     * @param frameLength 固定帧长度（字节数），必须 &gt; 0
     * @param charset     字符编码
     * @throws IllegalArgumentException 如果 frameLength &le; 0
     */
    public FixedLengthCodec(int frameLength, Charset charset) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException("frameLength must be positive: " + frameLength);
        }
        this.frameLength = frameLength;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    // ======================== encode ========================

    /**
     * 将消息编码为定长字节帧.
     *
     * <p>消息字符串转为字节后：
     * <ul>
     *   <li>若长度 &lt; frameLength，末尾填充 0x00</li>
     *   <li>若长度 == frameLength，直接返回</li>
     *   <li>若长度 &gt; frameLength，截断到 frameLength</li>
     * </ul>
     * </p>
     *
     * @param message 待编码的消息对象（会调用 toString 转为字符串）
     * @return 精确 frameLength 长度的字节数组
     * @throws NetException 如果消息为 {@code null}
     */
    @Override
    public byte[] encode(Object message) {
        if (message == null) {
            throw new NetException("Cannot encode null message with FixedLengthCodec");
        }
        byte[] data = message.toString().getBytes(charset);
        if (data.length == frameLength) {
            return data;
        }
        byte[] frame = new byte[frameLength];
        if (data.length < frameLength) {
            System.arraycopy(data, 0, frame, 0, data.length);
            // 剩余默认为 0x00（无需显式填充）
        } else {
            System.arraycopy(data, 0, frame, 0, frameLength);
        }
        return frame;
    }

    // ======================== decode ========================

    /**
     * 将定长字节帧解码为字符串.
     *
     * <p>去除尾部的 0x00 填充后按指定字符集转为字符串.</p>
     *
     * @param bytes 原始字节（长度可为任意值，按 frameLength 处理）
     * @return 解码后的字符串
     * @throws NetException 如果字节数组为 {@code null}
     */
    @Override
    public Object decode(byte[] bytes) {
        if (bytes == null) {
            throw new NetException("Cannot decode null bytes with FixedLengthCodec");
        }
        // 找到尾部第一个非 0x00 的位置
        int end = Math.min(bytes.length, frameLength);
        while (end > 0 && bytes[end - 1] == 0) {
            end--;
        }
        return new String(bytes, 0, end, charset);
    }

    // ======================== getProtocolName ========================

    @Override
    public String getProtocolName() {
        return "FixedLength(" + frameLength + "B)";
    }
}
