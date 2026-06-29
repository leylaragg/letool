package com.github.leyland.letool.net.tcp;

import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.pool.GenericConnectionPool;
import com.github.leyland.letool.net.pool.PoolConfig;
import com.github.leyland.letool.net.protocol.ProtocolCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 长连接 TCP 客户端 —— 维护一个持久化的 TCP 连接池，复用连接以提高高频通信的性能.
 *
 * <p>适用于高频请求场景（如实时交易系统、持续监控等），支持以下特性：</p>
 * <ul>
 *   <li>连接池复用 —— 借出/归还模式，避免频繁建立/断开连接</li>
 *   <li>心跳保活 —— 定时发送 PING 心跳包，防止 NAT/防火墙中断空闲连接</li>
 *   <li>连接验证 —— 借用前校验 Socket 有效性，自动剔除失效连接</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * TcpLongClient client = new TcpLongClient("192.168.1.100", 8088, jsonCodec, 2, 10);
 * byte[] response = client.sendAndReceive(requestBytes);
 * // ... 多次调用 ...
 * client.disconnect(); // 释放所有连接
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class TcpLongClient implements TcpClient {

    private static final Logger log = LoggerFactory.getLogger(TcpLongClient.class);

    // ======================== 字段 ========================

    /** 远程主机 */
    private final String host;

    /** 远程端口 */
    private final int port;

    /** 协议编解码器 */
    private final ProtocolCodec codec;

    /** 连接池配置 */
    private final PoolConfig poolConfig;

    /** 接收缓冲区大小 */
    private final int bufferSize;

    /** 连接池（内部管理 Socket 对象） */
    private GenericConnectionPool<Socket> pool;

    /** 心跳定时器 */
    private ScheduledExecutorService heartbeatExecutor;

    /** 是否已初始化 */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** 是否已关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 心跳间隔（秒），默认 30 */
    private final int heartbeatIntervalSec;

    /** 是否启用心跳 */
    private final boolean heartbeatEnabled;

    // ======================== 构造器 ========================

    /**
     * 构造长连接 TCP 客户端（使用默认配置）.
     *
     * @param host         远程主机
     * @param port         远程端口
     * @param codec        协议编解码器
     * @param poolMinIdle  连接池最小空闲连接数
     * @param poolMaxTotal 连接池最大连接数
     */
    public TcpLongClient(String host, int port, ProtocolCodec codec, int poolMinIdle, int poolMaxTotal) {
        this(host, port, codec, poolMinIdle, poolMaxTotal, 8192, true, 30);
    }

    /**
     * 全参构造.
     *
     * @param host                 远程主机
     * @param port                 远程端口
     * @param codec                协议编解码器
     * @param poolMinIdle          连接池最小空闲连接数
     * @param poolMaxTotal         连接池最大连接数
     * @param bufferSize           接收缓冲区大小
     * @param heartbeatEnabled     是否启用心跳
     * @param heartbeatIntervalSec 心跳间隔（秒）
     */
    public TcpLongClient(String host, int port, ProtocolCodec codec,
                         int poolMinIdle, int poolMaxTotal,
                         int bufferSize, boolean heartbeatEnabled, int heartbeatIntervalSec) {
        this.host = host;
        this.port = port;
        this.codec = codec;
        this.bufferSize = bufferSize > 0 ? bufferSize : 8192;
        this.heartbeatEnabled = heartbeatEnabled;
        this.heartbeatIntervalSec = heartbeatIntervalSec > 0 ? heartbeatIntervalSec : 30;
        this.poolConfig = PoolConfig.builder()
                .minIdle(poolMinIdle)
                .maxTotal(poolMaxTotal)
                .maxWaitMs(5000)
                .evictionIntervalMs(60_000)
                .build();
    }

    // ======================== connect ========================

    /**
     * 初始化连接池并建立最小空闲连接.
     *
     * @throws NetException 初始化失败时抛出
     */
    @Override
    public void connect() {
        if (!initialized.compareAndSet(false, true)) {
            log.debug("TcpLongClient already initialized");
            return;
        }
        // 创建连接工厂
        GenericConnectionPool.ConnectionFactory<Socket> factory = new GenericConnectionPool.ConnectionFactory<Socket>() {
            @Override
            public Socket createObject() throws Exception {
                Socket sock = new Socket();
                sock.setTcpNoDelay(true);
                sock.setKeepAlive(heartbeatEnabled);
                sock.connect(new InetSocketAddress(host, port), 10_000);
                log.debug("Created pooled connection to {}:{}", host, port);
                return sock;
            }

            @Override
            public boolean validateObject(Socket obj) {
                if (obj == null) {
                    return false;
                }
                try {
                    return obj.isConnected() && !obj.isClosed()
                            && obj.getInputStream() != null;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public void destroyObject(Socket obj) {
                if (obj != null && !obj.isClosed()) {
                    try {
                        obj.close();
                        log.debug("Destroyed pooled connection to {}:{}", host, port);
                    } catch (IOException e) {
                        log.warn("Error destroying connection: {}", e.getMessage());
                    }
                }
            }
        };

        this.pool = new GenericConnectionPool<>(poolConfig, factory);

        // 启动心跳
        if (heartbeatEnabled) {
            startHeartbeat();
        }
        log.info("TcpLongClient initialized, pool: minIdle={}, maxTotal={}", poolConfig.getMinIdle(), poolConfig.getMaxTotal());
    }

    // ======================== sendAndReceive ========================

    /**
     * 使用连接池发送请求并接收响应.
     *
     * <p>流程：borrow 连接 -> send -> receive -> return 连接</p>
     *
     * @param request 请求字节数组
     * @return 响应字节数组
     * @throws NetException 发送或接收失败时抛出（失败的连接会被 invalidate）
     */
    @Override
    public byte[] sendAndReceive(byte[] request) {
        if (request == null || request.length == 0) {
            throw new NetException("Request bytes must not be null or empty");
        }
        // 确保已初始化
        if (!initialized.get()) {
            connect();
        }
        checkNotClosed();

        Socket sock = null;
        try {
            sock = pool.borrowObject();

            // 发送
            OutputStream out = sock.getOutputStream();
            out.write(request);
            out.flush();
            log.trace("Sent {} bytes to {}:{}", request.length, host, port);

            // 接收
            InputStream in = sock.getInputStream();
            byte[] buffer = new byte[bufferSize];
            int totalRead = 0;
            int bytesRead;
            while (totalRead < buffer.length) {
                bytesRead = in.read(buffer, totalRead, buffer.length - totalRead);
                if (bytesRead < 0) {
                    break;
                }
                totalRead += bytesRead;
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

            // 正常归还
            pool.returnObject(sock);
            return response;
        } catch (Exception e) {
            // 通信异常，标记连接无效
            if (sock != null) {
                pool.invalidateObject(sock);
            }
            throw new NetException("TCP communication error with " + host + ":" + port, e);
        }
    }

    // ======================== disconnect ========================

    /**
     * 关闭连接池，释放所有 Socket 连接和心跳定时器.
     */
    @Override
    public void disconnect() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopHeartbeat();
        if (pool != null) {
            pool.close();
        }
        initialized.set(false);
        log.info("TcpLongClient disconnected from {}:{}", host, port);
    }

    // ======================== isConnected ========================

    /**
     * 判断连接池是否可用.
     *
     * @return {@code true} 如果已初始化且未关闭
     */
    @Override
    public boolean isConnected() {
        return initialized.get() && !closed.get();
    }

    // ======================== 心跳 ========================

    /**
     * 启动心跳定时任务.
     */
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tcp-long-heartbeat-" + host + ":" + port);
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (closed.get() || !initialized.get()) {
                return;
            }
            log.trace("Sending heartbeat to {}:{}", host, port);
            try {
                // 发送简单 PING 字节（可在子类覆盖）
                byte[] ping = "PING".getBytes(StandardCharsets.UTF_8);
                byte[] response = sendAndReceive(ping);
                log.trace("Heartbeat response: {} bytes", response != null ? response.length : 0);
            } catch (Exception e) {
                log.warn("Heartbeat failed for {}:{}: {}", host, port, e.getMessage());
            }
        }, heartbeatIntervalSec, heartbeatIntervalSec, TimeUnit.SECONDS);

        log.info("Heartbeat started, interval={}s", heartbeatIntervalSec);
    }

    /**
     * 停止心跳定时器.
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 校验客户端未关闭.
     *
     * @throws NetException 如果已关闭
     */
    private void checkNotClosed() {
        if (closed.get()) {
            throw new NetException("TcpLongClient has been closed");
        }
    }

    // ======================== Getter ========================

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * 获取连接池当前活跃连接数.
     *
     * @return 活跃连接数
     */
    public int getActiveCount() {
        return pool != null ? pool.getActiveCount() : 0;
    }

    /**
     * 获取连接池当前空闲连接数.
     *
     * @return 空闲连接数
     */
    public int getIdleCount() {
        return pool != null ? pool.getIdleCount() : 0;
    }
}
