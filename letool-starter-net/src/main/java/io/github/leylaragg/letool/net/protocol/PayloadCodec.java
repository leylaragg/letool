package io.github.leylaragg.letool.net.protocol;

/**
 * 业务对象与单个完整 TCP 报文载荷之间的编解码器。
 *
 * <p>同一实例可能被多个连接线程并发调用，因此实现必须是线程安全的。报文粘包和拆包
 * 由 {@link FrameCodec} 独立处理。</p>
 *
 * @param <REQ> 请求对象类型
 * @param <RESP> 响应对象类型
 */
public interface PayloadCodec<REQ, RESP> {

    /**
     * 将请求对象编码为业务报文字节。
     *
     * @param request 请求对象
     * @return 非空业务报文字节数组
     */
    byte[] encode(REQ request);

    /**
     * 将完整业务报文字节解码为响应对象。
     *
     * @param response 完整响应载荷
     * @return 响应对象
     */
    RESP decode(byte[] response);
}
