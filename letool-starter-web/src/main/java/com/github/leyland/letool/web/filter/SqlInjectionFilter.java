package com.github.leyland.letool.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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

        // 检查 URL 参数
        Map<String, String[]> params = httpRequest.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            for (String value : entry.getValue()) {
                if (containsSqlInjection(value)) {
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid request parameters");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean containsSqlInjection(String value) {
        if (value == null || value.isEmpty()) return false;
        return SQL_INJECTION_PATTERN.matcher(value).find();
    }
}
