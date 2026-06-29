package com.github.leyland.letool.net.tcp;

import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.protocol.ProtocolCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 短连接 TCP 客户端 —— 每次发送请求时临时建立连接，收到响应后立即关闭.
 *
 * <p>适合请求频率低、每次数据量较小的场景（如定时任务轮询、低频金融报文）.</p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * TcpShortClient client = new TcpShortClient("192.168.1.100", 8088, lengthFieldCodec, 5000, 30000);
 * byte[] response = client.sendAndReceive(requestBytes);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class TcpShortClient implements TcpClient {

    private static final Logger log = LoggerFactory.getLogger(TcpShortClient.class);

    // ======================== 字段 ========================

    /** 远程主机 */
    private final String host;

    /** 远程端口 */
    private final int port;

    /** 协议编解码器 */
    private final ProtocolCodec codec;

    /** 连接超时（毫秒） */
    private final int connectTimeoutMs;

    /** 读取超时（毫秒） */
    private final int readTimeoutMs;

    /** 当前 Socket（短连接模式下每次 send 会替换） */
    private volatile Socket socket;

    /** 接收缓冲区大小，默认 8192 */
    private final int bufferSize;

    // ======================== 构造器 ========================

    /**
     * 构造短连接 TCP 客户端.
     *
     * @param host             远程主机
     * @param port             远程端口
     * @param codec            协议编解码器
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public TcpShortClient(String host, int port, ProtocolCodec codec, int connectTimeoutMs, int readTimeoutMs) {
        this(host, port, codec, connectTimeoutMs, readTimeoutMs, 8192);
    }

    /**
     * 构造短连接 TCP 客户端（指定缓冲区大小）.
     *
     * @param host             远程主机
     * @param port             远程端口
     * @param codec            协议编解码器
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     * @param bufferSize       接收缓冲区大小
     */
    public TcpShortClient(String host, int port, ProtocolCodec codec,
                          int connectTimeoutMs, int readTimeoutMs, int bufferSize) {
        this.host = host;
        this.port = port;
        this.codec = codec;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.bufferSize = bufferSize > 0 ? bufferSize : 8192;
    }

    // ======================== connect ========================

    /**
     * 建立 TCP 连接.
     *
     * <p>短连接模式下通常不需要显式调用此方法，{@link #sendAndReceive(byte[])} 内部自动完成连接.</p>
     *
     * @throws NetException 如果连接失败
     */
    @Override
    public void connect() {
        try {
            Socket sock = new Socket();
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(readTimeoutMs);
            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            this.socket = sock;
            log.debug("TcpShortClient connected to {}:{}", host, port);
        } catch (IOException e) {
            throw new NetException("Failed to connect to " + host + ":" + port, e);
        }
    }

    // ======================== sendAndReceive ========================

    /**
     * 发送请求并接收响应（完整的短连接周期：connect -> send -> receive -> close）.
     *
     * @param request 请求字节数组
     * @return 响应字节数组
     * @throws NetException 发送或接收失败时抛出
     */
    @Override
    public byte[] sendAndReceive(byte[] request) {
        if (request == null || request.length == 0) {
            throw new NetException("Request bytes must not be null or empty");
        }
        connect();
        try {
            // 发送
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
            log.trace("Sent {} bytes to {}:{}", request.length, host, port);

            // 接收
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[bufferSize];
            int totalRead = 0;
            int bytesRead;
            // 读取直到对端关闭输出流或超时
            while (totalRead < buffer.length) {
                bytesRead = in.read(buffer, totalRead, buffer.length - totalRead);
                if (bytesRead < 0) {
                    break; // EOF
                }
                totalRead += bytesRead;
                // 短暂暂停等待更多数据（简单启发式）
                if (in.available() <= 0) {
                    break;
                }
            }
            if (totalRead == 0) {
                throw new NetException("No response received from " + host + ":" + port);
            }
            byte[] response = new byte[totalRead];
            System.arraycopy(buffer, 0, response, 0, totalRead);
            log.trace("Received {} bytes from {}:{}", totalRead, host, port);
            return response;
        } catch (IOException e) {
            throw new NetException("TCP communication error with " + host + ":" + port, e);
        } finally {
            disconnect();
        }
    }

    // ======================== disconnect ========================

    /**
     * 断开连接并释放 Socket 资源.
     */
    @Override
    public void disconnect() {
        Socket sock = this.socket;
        this.socket = null;
        if (sock != null && !sock.isClosed()) {
            try {
                sock.close();
                log.debug("TcpShortClient disconnected from {}:{}", host, port);
            } catch (IOException e) {
                log.warn("Error closing socket: {}", e.getMessage());
            }
        }
    }

    // ======================== isConnected ========================

    /**
     * 判断连接是否可用.
     *
     * @return {@code true} 如果 Socket 已连接且未关闭
     */
    @Override
    public boolean isConnected() {
        Socket sock = this.socket;
        return sock != null && sock.isConnected() && !sock.isClosed();
    }

    // ======================== Getter ========================

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
