package io.github.leylaragg.letool.web.filter;

import io.github.leylaragg.letool.web.exception.RequestBodyTooLargeException;
import io.github.leylaragg.letool.web.wrapper.RepeatableRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可重复读请求体的关键功能和资源边界测试。
 */
class RepeatableRequestFilterTest {

    /**
     * 验证允许的 JSON 请求会被包装，并按请求字符集支持重复读取。
     *
     * @throws Exception 过滤器或读取失败时抛出
     */
    @Test
    void shouldWrapAllowedJsonAndReadWithRequestCharset() throws Exception {
        MockHttpServletRequest request = request(MediaType.APPLICATION_JSON_VALUE, "中文内容");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        AtomicReference<ServletRequest> captured = new AtomicReference<>();
        FilterChain chain = (filteredRequest, response) -> captured.set(filteredRequest);
        RepeatableRequestFilter filter = filter(1024, List.of());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(captured.get()).isInstanceOf(RepeatableRequestWrapper.class);
        RepeatableRequestWrapper wrapped = (RepeatableRequestWrapper) captured.get();
        assertThat(wrapped.getReader().readLine()).isEqualTo("中文内容");
        assertThat(wrapped.getReader().readLine()).isEqualTo("中文内容");
    }

    /**
     * 验证未知长度请求也按实际读取字节数执行硬限制。
     */
    @Test
    void shouldRejectUnknownLengthWhenActualBodyExceedsLimit() {
        MockHttpServletRequest source = request(MediaType.TEXT_PLAIN_VALUE, "12345");
        HttpServletRequest unknownLength = new HttpServletRequestWrapper(source) {
            /**
             * 模拟分块传输或未声明 Content-Length 的请求。
             *
             * @return 固定返回 {@code -1}
             */
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };

        assertThatThrownBy(() -> new RepeatableRequestWrapper(unknownLength, 4))
                .isInstanceOfSatisfying(RequestBodyTooLargeException.class, exception -> {
                    assertThat(exception.getActualSize()).isEqualTo(5);
                    assertThat(exception.getMaxSize()).isEqualTo(4);
                });
    }

    /**
     * 验证 multipart、表单、二进制和排除路径始终绕过内存缓存。
     *
     * @throws Exception 过滤器执行失败时抛出
     */
    @Test
    void shouldBypassUnsafeContentTypesAndExcludedPaths() throws Exception {
        List<String> contentTypes = List.of(
                MediaType.MULTIPART_FORM_DATA_VALUE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.APPLICATION_OCTET_STREAM_VALUE);
        RepeatableRequestFilter filter = filter(1024, List.of("/upload/**"));

        for (String contentType : contentTypes) {
            MockHttpServletRequest request = request(contentType, "payload");
            assertThat(filteredRequest(filter, request)).isSameAs(request);
        }

        MockHttpServletRequest excluded = request(MediaType.APPLICATION_JSON_VALUE, "payload");
        excluded.setRequestURI("/upload/file");
        assertThat(filteredRequest(filter, excluded)).isSameAs(excluded);
    }

    /**
     * 验证请求体超限会交给 Spring MVC 异常解析器处理，且不会继续调用过滤器链。
     *
     * @throws Exception 过滤器执行失败时抛出
     */
    @Test
    void shouldDelegateBodyLimitFailureToMvcExceptionResolver() throws Exception {
        AtomicReference<Exception> resolved = new AtomicReference<>();
        HandlerExceptionResolver resolver = (request, response, handler, exception) -> {
            resolved.set(exception);
            return new ModelAndView();
        };
        RepeatableRequestFilter filter = new RepeatableRequestFilter(
                4,
                List.of(),
                List.of(MediaType.APPLICATION_JSON_VALUE),
                resolver);
        AtomicInteger chainInvocations = new AtomicInteger();
        FilterChain chain = (request, response) -> chainInvocations.incrementAndGet();

        filter.doFilter(
                request(MediaType.APPLICATION_JSON_VALUE, "12345"),
                new MockHttpServletResponse(),
                chain);

        assertThat(resolved.get()).isInstanceOf(RequestBodyTooLargeException.class);
        assertThat(chainInvocations).hasValue(0);
    }

    /**
     * 创建使用默认文本媒体类型规则的过滤器。
     *
     * @param maxBodySize 最大缓存字节数
     * @param excludePaths 排除路径
     * @return 可重复读请求体过滤器
     */
    private RepeatableRequestFilter filter(long maxBodySize, List<String> excludePaths) {
        HandlerExceptionResolver resolver = (request, response, handler, exception) -> null;
        return new RepeatableRequestFilter(
                maxBodySize,
                excludePaths,
                List.of(
                        "application/json",
                        "application/*+json",
                        "application/xml",
                        "application/*+xml",
                        "text/*"),
                resolver);
    }

    /**
     * 执行过滤器并返回传入下游链的请求对象。
     *
     * @param filter 被测过滤器
     * @param request 原始请求
     * @return 传入下游链的请求
     * @throws Exception 过滤器执行失败时抛出
     */
    private ServletRequest filteredRequest(
            RepeatableRequestFilter filter,
            MockHttpServletRequest request) throws Exception {
        AtomicReference<ServletRequest> captured = new AtomicReference<>();
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (filteredRequest, response) -> captured.set(filteredRequest));
        return captured.get();
    }

    /**
     * 创建带指定媒体类型和 UTF-8 请求体的 POST 请求。
     *
     * @param contentType 请求媒体类型
     * @param body 请求体文本
     * @return 模拟 HTTP 请求
     */
    private MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/body");
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
