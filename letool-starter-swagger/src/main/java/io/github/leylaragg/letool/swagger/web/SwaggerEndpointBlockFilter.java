package io.github.leylaragg.letool.swagger.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 在 Letool Swagger 关闭时隐藏标准 API 文档入口的 Servlet 过滤器。
 *
 * <p>该过滤器只负责实现 {@code letool.swagger.enabled=false} 的统一关闭语义，
 * 不承担生产环境的身份认证与授权职责。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class SwaggerEndpointBlockFilter implements Filter {

    /** 必须精确匹配并隐藏的文档入口。 */
    private final Set<String> exactPaths;

    /** 必须按路径前缀隐藏的文档入口。 */
    private final Set<String> pathPrefixes;

    /** Spring MVC DispatcherServlet 的标准化路径。 */
    private final String servletPath;

    /**
     * 根据 Springdoc 实际配置创建文档端点过滤器。
     *
     * @param apiDocsPath Springdoc OpenAPI 文档路径
     * @param swaggerUiPath Springdoc Swagger UI 兼容入口路径
     * @param servletPath Spring MVC DispatcherServlet 路径
     * @param groupNames Springdoc 真实文档分组名称
     */
    public SwaggerEndpointBlockFilter(
            String apiDocsPath,
            String swaggerUiPath,
            String servletPath,
            Collection<String> groupNames) {
        String normalizedApiDocsPath = normalizePath(apiDocsPath, "/v3/api-docs");
        String normalizedSwaggerUiPath = normalizePath(
                swaggerUiPath, "/swagger-ui.html");
        this.servletPath = normalizeServletPath(servletPath);

        Set<String> configuredExactPaths = new LinkedHashSet<>();
        configuredExactPaths.add("/doc.html");
        configuredExactPaths.add("/v3/api-docs");
        configuredExactPaths.add("/v3/api-docs.yaml");
        configuredExactPaths.add("/swagger-ui.html");
        configuredExactPaths.add(normalizedApiDocsPath);
        configuredExactPaths.add(normalizedApiDocsPath + ".yaml");
        configuredExactPaths.add(normalizedApiDocsPath + "/swagger-config");
        configuredExactPaths.add(normalizedSwaggerUiPath);
        if (groupNames != null) {
            groupNames.stream()
                    .filter(groupName -> groupName != null && !groupName.isBlank())
                    .map(String::trim)
                    .forEach(groupName -> {
                        configuredExactPaths.add(
                                normalizedApiDocsPath + "/" + groupName);
                        configuredExactPaths.add(
                                normalizedApiDocsPath + ".yaml/" + groupName);
                    });
        }
        this.exactPaths = Set.copyOf(configuredExactPaths);

        Set<String> configuredPrefixes = new LinkedHashSet<>();
        configuredPrefixes.add("/v3/api-docs/");
        configuredPrefixes.add("/v3/api-docs.yaml/");
        configuredPrefixes.add("/swagger-ui/");
        this.pathPrefixes = Set.copyOf(configuredPrefixes);
    }

    /**
     * 拦截标准文档端点，并放行业务请求和其他静态资源。
     *
     * @param request Servlet 请求
     * @param response Servlet 响应
     * @param chain 过滤器链
     * @throws IOException 响应写入或过滤器链处理失败时抛出
     * @throws ServletException Servlet 请求处理失败时抛出
     */
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String requestPath = extractRequestPath(httpRequest);
        if (isDocumentPath(requestPath)) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 提取不包含应用上下文路径的请求路径。
     *
     * @param request HTTP 请求
     * @return 相对于应用上下文的请求路径
     */
    private String extractRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null
                && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        if (!servletPath.isEmpty()) {
            if (requestUri.equals(servletPath)) {
                return "/";
            }
            if (requestUri.startsWith(servletPath + "/")) {
                return requestUri.substring(servletPath.length());
            }
        }
        return requestUri;
    }

    /**
     * 判断请求路径是否属于需要隐藏的标准文档端点。
     *
     * @param requestPath 相对于应用上下文的请求路径
     * @return {@code true} 表示应返回 404，{@code false} 表示继续过滤器链
     */
    private boolean isDocumentPath(String requestPath) {
        if (exactPaths.contains(requestPath)) {
            return true;
        }
        return pathPrefixes.stream().anyMatch(requestPath::startsWith);
    }

    /**
     * 将配置路径标准化为无尾斜杠的绝对路径。
     *
     * @param path 原始配置路径
     * @param defaultPath 配置为空时使用的默认路径
     * @return 标准化后的绝对路径
     */
    private String normalizePath(String path, String defaultPath) {
        if (path == null || path.isBlank()) {
            return defaultPath;
        }

        String normalizedPath = path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        while (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        return normalizedPath;
    }

    /**
     * 将 Spring MVC Servlet 路径标准化为空字符串或无尾斜杠的绝对路径。
     *
     * @param path 原始 Servlet 路径
     * @return 标准化后的 Servlet 路径；根路径返回空字符串
     */
    private String normalizeServletPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "";
        }
        return normalizePath(path, "");
    }
}
