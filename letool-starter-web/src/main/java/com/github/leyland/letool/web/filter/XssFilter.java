package com.github.leyland.letool.web.filter;

import com.github.leyland.letool.web.wrapper.XssRequestWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * XSS 防御过滤器 —— 包装 HttpServletRequest，对所有参数进行 HTML 转义.
 */
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            chain.doFilter(new XssRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
