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
        Integer headerVersion = parseVersion(header);
        if (headerVersion != null && headerVersion == this.version) {
            return this;
        }
        // 2. 从请求参数获取 apiVersion
        Integer parameterVersion = parseVersion(request.getParameter("apiVersion"));
        if (parameterVersion != null && parameterVersion == this.version) {
            return this;
        }
        return null;
    }

    @Override
    public int compareTo(ApiVersionRequestMapping other, HttpServletRequest request) {
        return Integer.compare(other.version, this.version);
    }

    /**
     * Parses an API version from a request header or query parameter.
     *
     * <p>Invalid or blank values are treated as no-match instead of propagating parsing exceptions
     * from Spring MVC's request mapping phase.</p>
     *
     * @param rawVersion raw version text supplied by the client
     * @return parsed major version, or {@code null} when the value is absent or invalid
     */
    private Integer parseVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PREFIX.matcher(rawVersion.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
