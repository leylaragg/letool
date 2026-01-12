package com.github.leyland.data.letool.tool.Interceptor;

import java.util.List;

/**
 * @ClassName <h2>InterceptorRule</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
public class InterceptorRule {

    private String host;  // 限制的 Host（域名）

    private List<String> includedPaths; // 需要拦截的 URI

    private List<String> excludedPaths; // 不拦截的 URI

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public List<String> getIncludedPaths() {
        return includedPaths;
    }

    public void setIncludedPaths(List<String> includedPaths) {
        this.includedPaths = includedPaths;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
