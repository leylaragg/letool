package com.github.leyland.letool.tool.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @ClassName <h2>HttpProperties</h2>
 * @Description
 * @Author rungo
 * @Date 2/27/2025
 * @Version 1.0
 **/
@ConfigurationProperties("spring.letool.http")
public class HttpProperties {

    private boolean enabled = false;

    private int connectTimeout;

    private int readTimeout;

    private int writeTimeout;

    private int maxTotalConnections;

    private int maxPerRouteConnections;

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public HttpProperties setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public HttpProperties setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public HttpProperties setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
        return this;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public HttpProperties setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
        return this;
    }

    public int getMaxPerRouteConnections() {
        return maxPerRouteConnections;
    }

    public HttpProperties setMaxPerRouteConnections(int maxPerRouteConnections) {
        this.maxPerRouteConnections = maxPerRouteConnections;
        return this;
    }
}
