package com.github.leyland.letool.net.protocol;

import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.util.ByteBufUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 分隔符协议编解码器 —— 消息以特定边界符分隔（如 {@code \r\n}、{@code \0}）.
 *
 * <p>编码规则：将消息字节 + 分隔符拼接.</p>
 * <p>解码规则：按分隔符查找第一个完整消息帧，返回分隔符之前的内容.</p>
 *
 * <h3>使用场景</h3>
 * <p>适用于 Telnet、SMTP、Redis RESP 等以换行符分隔的文本协议.</p>
 *
 * <h3>安全限制</h3>
 * <p>{@code maxFrameLength} 用于防止恶意超长消息导致 OOM.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DelimiterCodec implements ProtocolCodec {

    // ======================== 字段 ========================

    /** 分隔符字节 */
    private final byte[] delimiter;

    /** 最大帧长度（防止内存溢出），默认 64KB */
    private final int maxFrameLength;

    /** 字符编码，默认 UTF-8 */
    private final Charset charset;

    // ======================== 构造器 ========================

    /**
     * 构造分隔符编解码器.
     *
     * @param delimiter      分隔符字符串（如 {@code "\r\n"}）
     * @param maxFrameLength 最大帧长度（字节），防止 OOM
     * @param charset        字符编码
     * @throws IllegalArgumentException 如果 delimiter 为空
     */
    public DelimiterCodec(String delimiter, int maxFrameLength, Charset charset) {
        if (delimiter == null || delimiter.isEmpty()) {
            throw new IllegalArgumentException("delimiter must not be empty");
        }
        this.delimiter = delimiter.getBytes(charset != null ? charset : StandardCharsets.UTF_8);
        this.maxFrameLength = maxFrameLength > 0 ? maxFrameLength : 65536;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    /**
     * 构造分隔符编解码器（使用 UTF-8，最大帧长 64KB）.
     *
     * @param delimiter 分隔符字符串
     */
    public DelimiterCodec(String delimiter) {
        this(delimiter, 65536, StandardCharsets.UTF_8);
    }

    // ======================== encode ========================

    /**
     * 将消息编码为 "数据 + 分隔符" 格式.
     *
     * @param message 待编码的消息对象（会调用 toString 转为字符串）
     * @return 分隔符协议字节帧
     * @throws NetException 如果消息为 {@code null}
     */
    @Override
    public byte[] encode(Object message) {
        if (message == null) {
            throw new NetException("Cannot encode null message with DelimiterCodec");
        }
        byte[] data = message.toString().getBytes(charset);
        return ByteBufUtil.concat(data, delimiter);
    }

    // ======================== decode ========================

    /**
     * 从字节流中提取第一个由分隔符界定的完整消息帧.
     *
     * @param bytes 原始字节流（可能包含多个消息帧）
     * @return 第一个完整消息（不含分隔符）的字符串
     * @throws NetException 如果字节数组为 {@code null}、未找到分隔符或帧超长
     */
    @Override
    public Object decode(byte[] bytes) {
        if (bytes == null) {
            throw new NetException("Cannot decode null bytes with DelimiterCodec");
        }
        int idx = ByteBufUtil.indexOf(bytes, delimiter);
        if (idx < 0) {
            throw new NetException("No delimiter found in data");
        }
        if (idx > maxFrameLength) {
            throw new NetException("Frame exceeds maxFrameLength: " + idx + " > " + maxFrameLength);
        }
        byte[] frameData = ByteBufUtil.subArray(bytes, 0, idx);
        return new String(frameData, charset);
    }

    // ======================== getProtocolName ========================

    @Override
    public String getProtocolName() {
        return "Delimiter(" + new String(delimiter, charset).replace("\r", "\\r").replace("\n", "\\n") + ")";
    }
}
