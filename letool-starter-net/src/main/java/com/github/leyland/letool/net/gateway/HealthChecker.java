package com.github.leyland.letool.net.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 健康检查器 —— 定期对后端服务器执行 TCP 连接探测或 HTTP GET 探测，并更新健康状态.
 *
 * <p>使用 {@link ScheduledExecutorService} 定时调度健康检查任务.
 * 支持的检查类型：</p>
 * <ul>
 *   <li><b>tcp-connect</b> —— 尝试建立 TCP 连接，超时 3 秒</li>
 *   <li><b>http</b> —— 发送 HTTP GET 请求到指定路径，超时 3 秒</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    // ======================== 字段 ========================

    /** 定时调度器 */
    private ScheduledExecutorService scheduler;

    /** 是否正在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 健康检查超时（毫秒），默认 3000 */
    private final int checkTimeoutMs;

    // ======================== 构造器 ========================

    /**
     * 默认构造（检查超时 3 秒）.
     */
    public HealthChecker() {
        this(3000);
    }

    /**
     * 构造健康检查器.
     *
     * @param checkTimeoutMs 单次检查超时（毫秒）
     */
    public HealthChecker(int checkTimeoutMs) {
        this.checkTimeoutMs = checkTimeoutMs > 0 ? checkTimeoutMs : 3000;
    }

    // ======================== 启动 / 停止 ========================

    /**
     * 启动定时健康检查.
     *
     * @param servers         待检查的服务器列表
     * @param intervalSeconds 检查间隔（秒）
     */
    public void startChecking(List<BackendServer> servers, int intervalSeconds) {
        if (!running.compareAndSet(false, true)) {
            log.warn("HealthChecker already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-health-checker");
            t.setDaemon(true);
            return t;
        });

        int sec = Math.max(1, intervalSeconds);
        scheduler.scheduleAtFixedRate(() -> {
            for (BackendServer server : servers) {
                try {
                    boolean healthy;
                    if ("http".equalsIgnoreCase(server.getScheme())) {
                        healthy = checkHttp(server, "/health");
                    } else {
                        healthy = checkTcp(server);
                    }
                    server.setHealthy(healthy);
                    server.setLastHealthCheckTime(System.currentTimeMillis());
                    if (!healthy) {
                        log.warn("Health check FAILED: {}:{}", server.getHost(), server.getPort());
                    }
                } catch (Exception e) {
                    log.warn("Health check error for {}:{}: {}", server.getHost(), server.getPort(), e.getMessage());
                    server.setHealthy(false);
                    server.setLastHealthCheckTime(System.currentTimeMillis());
                }
            }
        }, sec, sec, TimeUnit.SECONDS);

        log.info("HealthChecker started, interval={}s, servers={}", sec, servers.size());
    }

    /**
     * 停止定时健康检查.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("HealthChecker stopped");
    }

    // ======================== 检查方法 ========================

    /**
     * TCP 连接健康检查 —— 尝试建立 Socket 连接.
     *
     * @param server 目标服务器
     * @return {@code true} 如果连接成功
     */
    public boolean checkTcp(BackendServer server) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), checkTimeoutMs);
            log.trace("TCP health check OK: {}:{}", server.getHost(), server.getPort());
            return true;
        } catch (IOException e) {
            log.trace("TCP health check FAILED: {}:{} - {}", server.getHost(), server.getPort(), e.getMessage());
            return false;
        }
    }

    /**
     * HTTP 健康检查 —— 发送 GET 请求到指定路径.
     *
     * @param server     目标服务器
     * @param healthPath 健康检查路径（如 "/health"）
     * @return {@code true} 如果返回 2xx 状态码
     */
    public boolean checkHttp(BackendServer server, String healthPath) {
        String path = healthPath != null ? healthPath : "/health";
        String urlStr = server.getScheme() + "://" + server.getHost() + ":" + server.getPort() + path;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(checkTimeoutMs);
            conn.setReadTimeout(checkTimeoutMs);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            conn.disconnect();
            boolean healthy = status >= 200 && status < 300;
            log.trace("HTTP health check {}: {} -> {}", healthy ? "OK" : "FAILED", urlStr, status);
            return healthy;
        } catch (IOException e) {
            log.trace("HTTP health check FAILED: {} - {}", urlStr, e.getMessage());
            return false;
        }
    }

    /**
     * 判断健康检查器是否正在运行.
     *
     * @return {@code true} 如果正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
