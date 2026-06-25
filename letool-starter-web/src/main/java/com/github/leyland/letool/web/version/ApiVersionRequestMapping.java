package com.github.leyland.letool.web.version;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 版本路由条件 —— 根据请求头 {@code X-API-Version} 或参数 {@code apiVersion} 匹配版本.
 */
public class ApiVersionRequestMapping implements RequestCondition<ApiVersionRequestMapping> {

    private static final Pattern VERSION_PREFIX = Pattern.compile("(\\d+).*");

    private final int version;

    public ApiVersionRequestMapping(int version) {
        this.version = version;
    }

    @Override
    public ApiVersionRequestMapping combine(ApiVersionRequestMapping other) {
        return new ApiVersionRequestMapping(other.version);
    }

    @Override
    public ApiVersionRequestMapping getMatchingCondition(HttpServletRequest request) {
        // 1. 从请求头获取 X-API-Version
        String header = request.getHeader("X-API-Version");
        if (header != null) {
            Matcher m = VERSION_PREFIX.matcher(header);
            if (m.find() && Integer.parseInt(m.group(1)) == this.version) {
                return this;
            }
        }
        // 2. 从请求参数获取 apiVersion
        String param = request.getParameter("apiVersion");
        if (param != null && Integer.parseInt(param) == this.version) {
            return this;
        }
        return null;
    }

    @Override
    public int compareTo(ApiVersionRequestMapping other, HttpServletRequest request) {
        return Integer.compare(other.version, this.version);
    }
}
