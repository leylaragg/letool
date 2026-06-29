package com.github.leyland.letool.net.tcp;

/**
 * TCP 客户端统一接口 —— 定义 TCP 通信的标准操作（连接、发送、接收、断开）.
 *
 * <p>所有 TCP 客户端实现（短连接 {@link TcpShortClient}、长连接池 {@link TcpLongClient}）
 * 均需实现此接口，确保上层调用方无需关心底层连接模式.</p>
 *
 * <h3>标准使用流程</h3>
 * <pre>{@code
 * TcpClient client = new TcpShortClient(host, port, codec, timeout, 500);
 * try {
 *     client.connect();
 *     byte[] response = client.sendAndReceive(request);
 * } finally {
 *     client.disconnect();
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface TcpClient {

    /**
     * 发送请求并同步等待返回响应.
     *
     * @param request 请求字节数组
     * @return 响应字节数组
     * @throws com.github.leyland.letool.net.exception.NetException 发送或接收失败时抛出
     */
    byte[] sendAndReceive(byte[] request);

    /**
     * 建立 TCP 连接.
     *
     * @throws com.github.leyland.letool.net.exception.NetException 连接失败时抛出
     */
    void connect();

    /**
     * 断开 TCP 连接（释放底层 Socket 资源）.
     */
    void disconnect();

    /**
     * 判断连接是否当前可用.
     *
     * @return {@code true} 如果 Socket 连通且未关闭
     */
    boolean isConnected();
}
