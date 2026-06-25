package com.github.leyland.letool.tool.http;

import java.time.Duration;

/**
 * HTTP 全局配置——控制超时、连接池大小、SSL 等默认行为.
 *
 * <p>可通过 {@link HttpUtil#getGlobalConfig()} 获取实例后直接修改，
 * 或通过 {@link HttpUtil#setGlobalConfig(HttpConfig)} 整体替换.
 * 修改后对所有通过 {@link HttpUtil} 发出的请求生效.</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * HttpConfig config = HttpUtil.getGlobalConfig();
 * config.setConnectTimeout(Duration.ofSeconds(3));
 * config.setReadTimeout(Duration.ofSeconds(10));
 * config.setMaxTotalConnections(500);
 * }</pre>
 *
 * <h3>配置项说明</h3>
 * <table>
 *   <tr><th>属性</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>connectTimeout</td><td>5s</td><td>TCP 连接建立超时</td></tr>
 *   <tr><td>readTimeout</td><td>30s</td><td>等待响应数据的超时</td></tr>
 *   <tr><td>writeTimeout</td><td>30s</td><td>写入请求体的超时</td></tr>
 *   <tr><td>maxTotalConnections</td><td>200</td><td>连接池最大连接数</td></tr>
 *   <tr><td>maxPerRoute</td><td>50</td><td>每个目标主机的最大连接数</td></tr>
 *   <tr><td>idleTimeout</td><td>60s</td><td>空闲连接保活时间</td></tr>
 *   <tr><td>trustAllCerts</td><td>false</td><td>是否信任所有 SSL 证书（仅开发环境）</td></tr>
 * </table>
 */
public class HttpConfig {

    /** TCP 连接建立超时（默认 5 秒） */
    private Duration connectTimeout = Duration.ofSeconds(5);
    /** 等待响应数据的超时（默认 30 秒） */
    private Duration readTimeout = Duration.ofSeconds(30);
    /** 写入请求体的超时（默认 30 秒） */
    private Duration writeTimeout = Duration.ofSeconds(30);
    /** 连接池最大连接数 */
    private int maxTotalConnections = 200;
    /** 每个路由（目标主机）的最大连接数 */
    private int maxPerRoute = 50;
    /** 空闲连接驱逐超时 */
    private Duration idleTimeout = Duration.ofSeconds(60);
    /** 是否信任所有 SSL 证书（仅开发环境，生产必须为 false） */
    private boolean trustAllCerts = false;

    // ======================== getter / setter ========================

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getWriteTimeout() { return writeTimeout; }
    public void setWriteTimeout(Duration writeTimeout) { this.writeTimeout = writeTimeout; }
    public int getMaxTotalConnections() { return maxTotalConnections; }
    public void setMaxTotalConnections(int maxTotalConnections) { this.maxTotalConnections = maxTotalConnections; }
    public int getMaxPerRoute() { return maxPerRoute; }
    public void setMaxPerRoute(int maxPerRoute) { this.maxPerRoute = maxPerRoute; }
    public Duration getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
    public boolean isTrustAllCerts() { return trustAllCerts; }
    public void setTrustAllCerts(boolean trustAllCerts) { this.trustAllCerts = trustAllCerts; }
}
