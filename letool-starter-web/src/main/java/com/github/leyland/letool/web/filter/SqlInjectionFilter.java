package com.github.leyland.letool.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL 注入防御过滤器 —— 检测请求参数和请求体中是否包含 SQL 注入关键字.
 *
 * <p>检测到可疑关键字时直接返回 HTTP 400.</p>
 */
public class SqlInjectionFilter implements Filter {

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(SELECT|INSERT|DELETE|UPDATE|DROP|ALTER|TRUNCATE|UNION|EXEC|EXECUTE)\\b)"
                    + "|(--)|(;)|('(''|[^'])*')|(/\\*.*\\*/)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 检查 URL 参数
        Map<String, String[]> params = httpRequest.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            for (String value : entry.getValue()) {
                if (containsSqlInjection(value)) {
                    httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request parameters");
                    return;
                }
            }
        }

        if (shouldInspectBody(httpRequest) && containsSqlInjection(readBody(httpRequest))) {
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean containsSqlInjection(String value) {
        if (value == null || value.isEmpty()) return false;
        return SQL_INJECTION_PATTERN.matcher(value).find();
    }

    /**
     * 判断当前请求体是否适合按文本进行 SQL 注入检测.
     *
     * <p>默认自动配置会先执行 {@link RepeatableRequestFilter}，因此这里读取请求体后，
     * 后续过滤器和 MVC 控制器仍然可以再次读取。</p>
     *
     * @param request HTTP 请求
     * @return {@code true} 表示需要读取并检测请求体
     */
    private boolean shouldInspectBody(HttpServletRequest request) {
        if (!hasBodyMethod(request.getMethod()) || request.getContentLengthLong() == 0) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("text/")
                || normalized.contains("json")
                || normalized.contains("xml")
                || normalized.contains("x-www-form-urlencoded");
    }

    private boolean hasBodyMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private String readBody(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0) {
            return "";
        }
        return new String(body, resolveCharset(request));
    }

    private Charset resolveCharset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
