package com.github.leyland.letool.web.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Objects;

/**
 * 使用 Spring {@link PathPattern} 匹配应用内请求路径。
 *
 * <p>所有表达式在构造阶段完成编译，避免每次请求重复解析，也让错误配置在应用启动时
 * 立即暴露。匹配路径会去除 context path，因此配置可以在不同部署上下文间复用。</p>
 */
public final class WebPathMatcher {

    /** 已完成编译的不可变路径规则。 */
    private final List<PathPattern> patterns;

    /**
     * 创建路径匹配器并编译全部表达式。
     *
     * @param pathPatterns 非空路径表达式列表；允许传入空列表
     * @throws NullPointerException 当列表或列表元素为 {@code null} 时抛出
     * @throws IllegalArgumentException 当表达式为空、不是绝对路径或语法非法时抛出
     */
    public WebPathMatcher(List<String> pathPatterns) {
        Objects.requireNonNull(pathPatterns, "pathPatterns");
        PathPatternParser parser = new PathPatternParser();
        this.patterns = pathPatterns.stream()
                .map(pattern -> parsePattern(parser, pattern))
                .toList();
    }

    /**
     * 判断请求的应用内路径是否命中任一规则。
     *
     * @param request 非空 HTTP 请求
     * @return 命中任一规则时返回 {@code true}
     * @throws NullPointerException 当请求为 {@code null} 时抛出
     */
    public boolean matches(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        if (patterns.isEmpty()) {
            return false;
        }
        PathContainer requestPath = PathContainer.parsePath(applicationPath(request));
        return patterns.stream().anyMatch(pattern -> pattern.matches(requestPath));
    }

    /**
     * 编译并校验单个路径表达式。
     *
     * @param parser Spring 路径表达式解析器
     * @param pattern 原始路径表达式
     * @return 编译后的路径表达式
     */
    private static PathPattern parsePattern(PathPatternParser parser, String pattern) {
        Objects.requireNonNull(pattern, "pathPattern");
        if (pattern.isBlank() || !pattern.startsWith("/")) {
            throw new IllegalArgumentException("Web 路径表达式必须是以 / 开头的非空绝对路径: " + pattern);
        }
        try {
            return parser.parse(pattern);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("非法 Web 路径表达式: " + pattern, exception);
        }
    }

    /**
     * 获取不包含 context path 的应用内请求路径。
     *
     * @param request HTTP 请求
     * @return 以 {@code /} 开头的应用内路径
     */
    private static String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            String path = requestUri.substring(contextPath.length());
            return path.isEmpty() ? "/" : path;
        }
        return requestUri == null || requestUri.isEmpty() ? "/" : requestUri;
    }
}
