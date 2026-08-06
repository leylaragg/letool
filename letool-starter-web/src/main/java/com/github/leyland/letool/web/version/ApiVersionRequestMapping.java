package com.github.leyland.letool.web.version;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据请求头或查询参数匹配 API 主版本的 Spring MVC 请求条件。
 *
 * <p>请求版本必须是完整的数字点分格式，例如 {@code 1}、{@code 1.2} 或 {@code 1.2.3}，
 * 路由时只使用第一段主版本。非空请求头拥有绝对优先级；请求头非法时不会回退到查询参数，
 * 避免同一请求携带矛盾版本来源时产生不确定路由。</p>
 */
public final class ApiVersionRequestMapping implements RequestCondition<ApiVersionRequestMapping> {

    /** 严格数字点分版本格式。 */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.\\d+)*$");

    /** 当前映射声明的主版本。 */
    private final int version;

    /** 版本请求头名称。 */
    private final String headerName;

    /** 版本查询参数名称。 */
    private final String parameterName;

    /**
     * 创建 API 版本请求条件。
     *
     * @param version 大于 0 的主版本
     * @param headerName 非空白版本请求头名称
     * @param parameterName 非空白版本查询参数名称
     * @throws IllegalArgumentException 当任一参数不合法时抛出
     */
    public ApiVersionRequestMapping(int version, String headerName, String parameterName) {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.headerName = requireName(headerName, "headerName");
        this.parameterName = requireName(parameterName, "parameterName");
    }

    /**
     * 合并类级和方法级版本条件，方法级条件覆盖类级条件。
     *
     * @param other 方法级版本条件
     * @return 方法级版本条件
     */
    @Override
    public ApiVersionRequestMapping combine(ApiVersionRequestMapping other) {
        return Objects.requireNonNull(other, "other");
    }

    /**
     * 判断当前请求的主版本是否与声明版本一致。
     *
     * @param request 当前 HTTP 请求
     * @return 匹配时返回当前条件，否则返回 {@code null}
     */
    @Override
    public ApiVersionRequestMapping getMatchingCondition(HttpServletRequest request) {
        String headerVersion = request.getHeader(headerName);
        Integer requestVersion;
        if (StringUtils.hasText(headerVersion)) {
            requestVersion = parseVersion(headerVersion);
        } else {
            requestVersion = parseVersion(request.getParameter(parameterName));
        }
        return requestVersion != null && requestVersion == version ? this : null;
    }

    /**
     * 在多个已匹配版本条件间提供确定性顺序。
     *
     * @param other 另一个版本条件
     * @param request 当前 HTTP 请求
     * @return 版本倒序比较结果
     */
    @Override
    public int compareTo(ApiVersionRequestMapping other, HttpServletRequest request) {
        return Integer.compare(other.version, version);
    }

    /**
     * 按版本值和版本来源配置判断条件是否相同。
     *
     * @param object 待比较对象
     * @return 条件完全相同时返回 {@code true}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ApiVersionRequestMapping other)) {
            return false;
        }
        return version == other.version
                && headerName.equals(other.headerName)
                && parameterName.equals(other.parameterName);
    }

    /**
     * 生成与相等性字段一致的哈希值。
     *
     * @return 条件哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(version, headerName, parameterName);
    }

    /**
     * 输出适合启动映射冲突诊断的条件描述。
     *
     * @return 包含版本和来源名称的描述
     */
    @Override
    public String toString() {
        return "[version=" + version
                + ", header=" + headerName
                + ", parameter=" + parameterName + ']';
    }

    /**
     * 严格解析数字点分版本的主版本。
     *
     * @param rawVersion 客户端版本文本
     * @return 主版本；缺失、非法或溢出时返回 {@code null}
     */
    private static Integer parseVersion(String rawVersion) {
        if (!StringUtils.hasText(rawVersion)) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(rawVersion.strip());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 校验并规范化版本来源名称。
     *
     * @param value 原始名称
     * @param argumentName 参数名称
     * @return 去除首尾空白的名称
     */
    private static String requireName(String value, String argumentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(argumentName + " must not be blank");
        }
        return value.strip();
    }
}
