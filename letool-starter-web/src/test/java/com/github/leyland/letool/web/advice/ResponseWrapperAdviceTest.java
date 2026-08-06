package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.web.annotation.ExcludeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应包装的关键边界测试。
 */
class ResponseWrapperAdviceTest {

    /** 被测响应包装器。 */
    private ResponseWrapperAdvice advice;

    /** 模拟 Servlet 请求。 */
    private MockHttpServletRequest servletRequest;

    /** 模拟 Servlet 响应。 */
    private MockHttpServletResponse servletResponse;

    /**
     * 初始化带路径排除规则的响应包装器。
     */
    @BeforeEach
    void setUp() {
        advice = new ResponseWrapperAdvice(List.of("/actuator/**"));
        servletRequest = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/users");
        servletResponse = new MockHttpServletResponse();
    }

    /**
     * 验证普通 JSON 对象会被包装为统一响应。
     *
     * @throws Exception 反射查找测试方法失败时抛出
     */
    @Test
    void shouldWrapOrdinaryJsonBody() throws Exception {
        Object body = new Object();

        Object result = write(body, MediaType.APPLICATION_JSON, "objectBody");

        assertThat(result).isInstanceOfSatisfying(R.class,
                response -> assertThat(response.getData()).isSameAs(body));
    }

    /**
     * 验证下载、原始文本、统一响应和 Spring 错误协议不会被二次包装。
     *
     * @throws Exception 反射查找测试方法失败时抛出
     */
    @Test
    void shouldKeepSpecialResponseBodiesUnchanged() throws Exception {
        R<Void> wrapped = R.ok();
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1});
        byte[] bytes = new byte[]{1};
        String text = "plain";
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        SseEmitter emitter = new SseEmitter();

        assertThat(write(wrapped, MediaType.APPLICATION_JSON, "objectBody")).isSameAs(wrapped);
        assertThat(write(resource, MediaType.APPLICATION_OCTET_STREAM, "objectBody")).isSameAs(resource);
        assertThat(write(bytes, MediaType.APPLICATION_OCTET_STREAM, "objectBody")).isSameAs(bytes);
        assertThat(write(text, MediaType.TEXT_PLAIN, "stringBody")).isSameAs(text);
        assertThat(write(problemDetail, MediaType.APPLICATION_PROBLEM_JSON, "objectBody"))
                .isSameAs(problemDetail);
        assertThat(write(emitter, MediaType.TEXT_EVENT_STREAM, "objectBody")).isSameAs(emitter);
    }

    /**
     * 验证路径规则匹配去除 context path 后的应用内路径。
     *
     * @throws Exception 反射查找测试方法失败时抛出
     */
    @Test
    void shouldExcludeConfiguredApplicationPath() throws Exception {
        Object body = new Object();
        servletRequest.setContextPath("/demo");
        servletRequest.setRequestURI("/demo/actuator/health");

        assertThat(write(body, MediaType.APPLICATION_JSON, "objectBody")).isSameAs(body);
    }

    /**
     * 验证显式排除注解、HEAD 和无内容响应不会被包装。
     *
     * @throws Exception 反射查找测试方法失败时抛出
     */
    @Test
    void shouldRespectExplicitAndHttpSemanticExclusions() throws Exception {
        MethodParameter excludedMethod = returnType("excludedBody");
        assertThat(advice.supports(excludedMethod, MappingJackson2HttpMessageConverter.class)).isFalse();

        Object body = new Object();
        servletRequest.setMethod(HttpMethod.HEAD.name());
        assertThat(write(body, MediaType.APPLICATION_JSON, "objectBody")).isSameAs(body);

        servletRequest.setMethod(HttpMethod.GET.name());
        servletResponse.setStatus(HttpStatus.NO_CONTENT.value());
        assertThat(write(body, MediaType.APPLICATION_JSON, "objectBody")).isSameAs(body);
    }

    /**
     * 调用响应包装器的写出阶段。
     *
     * @param body 原始响应体
     * @param mediaType 最终响应媒体类型
     * @param methodName 测试控制器方法名
     * @return 包装器处理后的响应体
     * @throws Exception 反射查找测试方法失败时抛出
     */
    private Object write(Object body, MediaType mediaType, String methodName) throws Exception {
        return advice.beforeBodyWrite(
                body,
                returnType(methodName),
                mediaType,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse));
    }

    /**
     * 获取测试控制器方法的返回值参数描述。
     *
     * @param methodName 方法名
     * @return 方法返回值参数描述
     * @throws NoSuchMethodException 找不到方法时抛出
     */
    private MethodParameter returnType(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new MethodParameter(method, -1);
    }

    /**
     * 为返回类型和注解检查提供的测试控制器。
     */
    private static final class TestController {

        /**
         * 返回普通对象。
         *
         * @return 普通对象
         */
        Object objectBody() {
            return null;
        }

        /**
         * 返回原始文本。
         *
         * @return 原始文本
         */
        String stringBody() {
            return null;
        }

        /**
         * 返回显式排除包装的对象。
         *
         * @return 普通对象
         */
        @ExcludeWrapper
        Object excludedBody() {
            return null;
        }
    }
}
