package io.github.leylaragg.letool.log.web;

import io.github.leylaragg.letool.log.config.LogProperties;
import io.github.leylaragg.letool.log.trace.TraceContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Web 请求日志过滤器。
 *
 * <p>过滤器覆盖完整的 Servlet 请求链，可在请求完成后读取真实响应状态，
 * 并避免 Controller AOP 无法覆盖异常处理和响应写入阶段的问题。</p>
 */
public class WebLogFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(WebLogFilter.class);

    private final List<PathPattern> excludePatterns;

    /**
     * 使用指定日志配置创建 Web 请求日志过滤器。
     *
     * @param properties 日志模块配置，不允许为 {@code null}
     */
    public WebLogFilter(LogProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        List<String> excludePaths = properties.getWebLog().getExcludePaths();
        if (excludePaths == null || excludePaths.isEmpty()) {
            this.excludePatterns = List.of();
            return;
        }
        this.excludePatterns = excludePaths.stream()
                .map(this::parseExcludePattern)
                .toList();
    }

    /**
     * 校验并编译单个排除路径表达式。
     *
     * @param path 用户配置的排除路径
     * @return 编译后的 Spring 路径模式
     * @throws NullPointerException 路径为 {@code null} 时抛出
     * @throws IllegalArgumentException 路径为空白时抛出
     */
    private PathPattern parseExcludePattern(String path) {
        Objects.requireNonNull(path, "excludePath must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("excludePath must not be blank");
        }
        return PathPatternParser.defaultInstance.parse(path);
    }

    /**
     * 判断当前请求是否命中用户配置的排除路径。
     *
     * @param request 当前 HTTP 请求
     * @return {@code true} 表示跳过 Web 请求日志
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (excludePatterns.isEmpty()) {
            return false;
        }
        String applicationPath = resolveApplicationPath(request);
        PathContainer pathContainer = PathContainer.parsePath(applicationPath);
        return excludePatterns.stream().anyMatch(pattern -> pattern.matches(pathContainer));
    }

    /**
     * 执行一次完整的 HTTP 请求过滤链。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException Servlet 请求处理失败时抛出
     * @throws IOException 请求或响应读写失败时抛出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = "-";
        }
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                registerAsyncListener(request, response, traceId, startNanos);
            } else {
                logCompletedRequest(
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        traceId,
                        startNanos,
                        null);
            }
        } catch (IOException | ServletException | RuntimeException | Error exception) {
            int status = response.getStatus() >= 400
                    ? response.getStatus()
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            logCompletedRequest(
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    traceId,
                    startNanos,
                    exception);
            throw exception;
        }
    }

    /**
     * 获取不包含 Servlet context-path 的应用内请求路径。
     *
     * @param request 当前 HTTP 请求
     * @return 以斜杠开头的应用内请求路径
     */
    private String resolveApplicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null
                && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)) {
            String applicationPath = requestUri.substring(contextPath.length());
            return applicationPath.isEmpty() ? "/" : applicationPath;
        }
        return requestUri.isEmpty() ? "/" : requestUri;
    }

    /**
     * 根据响应状态和异常情况选择日志级别并记录请求结果。
     *
     * @param method HTTP 请求方法
     * @param requestUri HTTP 请求 URI
     * @param status HTTP 响应状态
     * @param traceId 当前链路标识
     * @param startNanos 请求起始纳秒时间
     * @param exception 请求处理异常；正常完成时为 {@code null}
     */
    private void logCompletedRequest(
            String method,
            String requestUri,
            int status,
            String traceId,
            long startNanos,
            Throwable exception) {
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        int effectiveStatus = exception != null && status < 400
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                : status;
        if (exception != null) {
            LOG.error("[{}] {} {} → {}，耗时: {}ms", traceId, method,
                    requestUri, effectiveStatus, duration, exception);
        } else if (effectiveStatus >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            LOG.error("[{}] {} {} → {}，耗时: {}ms", traceId, method,
                    requestUri, effectiveStatus, duration);
        } else if (effectiveStatus >= HttpServletResponse.SC_BAD_REQUEST) {
            LOG.warn("[{}] {} {} → {}，耗时: {}ms", traceId, method,
                    requestUri, effectiveStatus, duration);
        } else {
            LOG.info("[{}] {} {} → {}，耗时: {}ms", traceId, method,
                    requestUri, effectiveStatus, duration);
        }
    }

    /**
     * 为异步请求注册完成监听器，确保日志覆盖真实异步处理耗时和最终响应状态。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param traceId 当前链路标识
     * @param startNanos 请求起始纳秒时间
     */
    private void registerAsyncListener(
            HttpServletRequest request,
            HttpServletResponse response,
            String traceId,
            long startNanos) {
        AsyncRequestLogListener listener = new AsyncRequestLogListener(
                request.getMethod(),
                request.getRequestURI(),
                response,
                traceId,
                startNanos);
        try {
            request.getAsyncContext().addListener(listener);
        } catch (IllegalStateException exception) {
            // 异步任务可能在注册监听器前已完成，此时按当前最终状态立即补记日志。
            logCompletedRequest(
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    traceId,
                    startNanos,
                    null);
        }
    }

    /**
     * 监听 Servlet 异步请求的最终完成、错误和超时事件。
     */
    private final class AsyncRequestLogListener implements AsyncListener {

        private final String method;
        private final String requestUri;
        private final HttpServletResponse response;
        private final String traceId;
        private final long startNanos;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        /**
         * 创建异步请求日志监听器。
         *
         * @param method HTTP 请求方法
         * @param requestUri HTTP 请求 URI
         * @param response 当前 HTTP 响应
         * @param traceId 当前链路标识
         * @param startNanos 请求起始纳秒时间
         */
        private AsyncRequestLogListener(
                String method,
                String requestUri,
                HttpServletResponse response,
                String traceId,
                long startNanos) {
            this.method = method;
            this.requestUri = requestUri;
            this.response = response;
            this.traceId = traceId;
            this.startNanos = startNanos;
        }

        /**
         * 异步请求完成后记录一次最终日志。
         *
         * @param event Servlet 异步事件
         */
        @Override
        public void onComplete(AsyncEvent event) {
            logCompletedRequest(
                    method,
                    requestUri,
                    response.getStatus(),
                    traceId,
                    startNanos,
                    failure.get());
        }

        /**
         * 保存异步处理异常，等待完成事件统一输出日志。
         *
         * @param event Servlet 异步错误事件
         */
        @Override
        public void onError(AsyncEvent event) {
            Throwable exception = event.getThrowable();
            if (exception == null) {
                exception = new ServletException("异步请求处理失败");
            }
            failure.compareAndSet(null, exception);
        }

        /**
         * 将异步超时保存为失败原因，等待完成事件统一输出日志。
         *
         * @param event Servlet 异步超时事件
         */
        @Override
        public void onTimeout(AsyncEvent event) {
            failure.compareAndSet(null, new ServletException("异步请求处理超时"));
        }

        /**
         * 异步处理重新启动时将当前监听器注册到新的 AsyncContext。
         *
         * @param event Servlet 新一轮异步事件
         */
        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
