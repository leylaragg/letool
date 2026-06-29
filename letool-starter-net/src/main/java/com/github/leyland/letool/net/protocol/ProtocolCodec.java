package com.github.leyland.letool.net.protocol;

/**
 * 协议编解码器接口 —— 定义网络通信中消息的编码（序列化）和解码（反序列化）协议.
 *
 * <p>所有自定义协议必须实现此接口，框架据此完成 Java 对象与网络字节流之间的双向转换.</p>
 *
 * <h3>已内置实现</h3>
 * <ul>
 *   <li>{@link FixedLengthCodec} —— 定长帧协议</li>
 *   <li>{@link DelimiterCodec} —— 分隔符协议</li>
 *   <li>{@link LengthFieldCodec} —— 长度字段协议（最常用）</li>
 *   <li>{@link JsonProtocolCodec} —— JSON 协议</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface ProtocolCodec {

    /**
     * 将消息对象编码为字节数组.
     *
     * @param message 待编码的消息对象
     * @return 编码后的字节数组
     * @throws com.github.leyland.letool.net.exception.NetException 编码失败时抛出
     */
    byte[] encode(Object message);

    /**
     * 将字节数组解码为消息对象.
     *
     * @param bytes 待解码的原始字节
     * @return 解码后的消息对象
     * @throws com.github.leyland.letool.net.exception.NetException 解码失败时抛出
     */
    Object decode(byte[] bytes);

    /**
     * 获取协议名称，用于日志和诊断.
     *
     * @return 协议名称，如 "JSON"、"LengthField" 等
     */
    String getProtocolName();
}
