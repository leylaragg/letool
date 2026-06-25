package com.github.leyland.letool.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * 可重复读请求体过滤器 —— 包装 HttpServletRequest，使请求体可多次读取.
 *
 * <p>用于需要在 Filter 和 Controller 中多次读取请求体的场景（如日志记录 + 业务处理）.</p>
 */
public class RepeatableRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest
                && !(request instanceof RepeatableRequestWrapper)) {
            chain.doFilter(new RepeatableRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 可重复读的 HttpServletRequestWrapper —— 缓存请求体字节数组.
     */
    private static class RepeatableRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;

        public RepeatableRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            return new jakarta.servlet.ServletInputStream() {
                private int pos;

                @Override
                public boolean isFinished() { return pos >= body.length; }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {}

                @Override
                public int read() {
                    return pos < body.length ? body[pos++] & 0xFF : -1;
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream()));
        }
    }
}
