package com.github.leyland.letool.log.web;

import com.github.leyland.letool.log.config.LogProperties;
import com.github.leyland.letool.log.trace.TraceContext;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockAsyncContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code WebLogFilter} Web 请求日志过滤器行为测试。
 */
@ExtendWith(OutputCaptureExtension.class)
class WebLogFilterTest {

    /**
     * 每个测试结束后清理当前线程的链路上下文。
     */
    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    /**
     * Web 日志应由 Servlet 过滤器实现，以便覆盖完整请求链并获得最终响应状态。
     */
    @Test
    void shouldProvideWebLogFilter() {
        assertThatCode(() -> Class.forName(
                "com.github.leyland.letool.log.web.WebLogFilter"))
                .doesNotThrowAnyException();
    }

    /**
     * 请求完成后应记录过滤链实际写入的 HTTP 状态码。
     *
     * @param output 当前测试捕获的日志输出
     * @throws Exception 过滤链执行失败时抛出
     */
    @Test
    void shouldLogActualResponseStatus(CapturedOutput output) throws Exception {
        WebLogFilter filter = new WebLogFilter(new LogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TraceContext.setTraceId("trace-web");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(204));

        assertThat(output)
                .contains("[trace-web]")
                .contains("POST /orders")
                .contains("204")
                .contains("耗时");
    }

    /**
     * 排除路径应使用应用内路径匹配，并支持标准的双星号路径模式。
     *
     * @param output 当前测试捕获的日志输出
     * @throws Exception 过滤链执行失败时抛出
     */
    @Test
    void shouldExcludePathWithSpringPathPattern(CapturedOutput output) throws Exception {
        LogProperties properties = new LogProperties();
        properties.getWebLog().setExcludePaths(List.of("/actuator/**"));
        WebLogFilter filter = new WebLogFilter(properties);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/app/actuator/health");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(output).doesNotContain("/actuator/health");
    }

    /**
     * 空白排除路径应在创建过滤器时明确拒绝，避免配置存在但永远无法匹配。
     */
    @Test
    void shouldRejectBlankExcludePath() {
        LogProperties properties = new LogProperties();
        properties.getWebLog().setExcludePaths(List.of(" "));

        assertThatThrownBy(() -> new WebLogFilter(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excludePath must not be blank");
    }

    /**
     * 过滤链异常时应记录完整异常堆栈，并继续向 Servlet 容器抛出原始异常。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldLogExceptionStackAndRethrowOriginalException(CapturedOutput output) {
        WebLogFilter filter = new WebLogFilter(new LogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    throw new ServletException("downstream failed");
                }))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream failed");

        assertThat(output)
                .contains("GET /orders")
                .contains("500")
                .contains("jakarta.servlet.ServletException: downstream failed");
    }

    /**
     * 异步请求应在 AsyncContext 真正完成后记录日志，不能只统计启动异步处理的耗时。
     *
     * @param output 当前测试捕获的日志输出
     * @throws Exception 过滤链执行失败时抛出
     */
    @Test
    void shouldLogAsyncRequestAfterCompletion(CapturedOutput output) throws Exception {
        WebLogFilter filter = new WebLogFilter(new LogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/async-orders");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletRequest) servletRequest).startAsync());

        assertThat(output).doesNotContain("/async-orders");

        AsyncContext asyncContext = request.getAsyncContext();
        response.setStatus(202);
        asyncContext.complete();

        assertThat(output)
                .contains("GET /async-orders")
                .contains("202");
    }

    /**
     * 异步错误事件未提供异常对象时，也应按失败请求记录安全的错误原因和 500 状态。
     *
     * @param output 当前测试捕获的日志输出
     * @throws Exception 过滤链或异步监听器执行失败时抛出
     */
    @Test
    void shouldTreatAsyncErrorWithoutThrowableAsFailure(CapturedOutput output) throws Exception {
        WebLogFilter filter = new WebLogFilter(new LogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/async-error");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletRequest) servletRequest).startAsync());

        MockAsyncContext asyncContext = (MockAsyncContext) request.getAsyncContext();
        AsyncListener listener = asyncContext.getListeners().get(0);
        listener.onError(new AsyncEvent(asyncContext, request, response, null));
        asyncContext.complete();

        assertThat(output)
                .contains("GET /async-error")
                .contains("500")
                .contains("异步请求处理失败");
    }
}
