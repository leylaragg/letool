package io.github.leylaragg.letool.web.advice;

import io.github.leylaragg.letool.tool.model.R;
import io.github.leylaragg.letool.web.annotation.ExcludeWrapper;
import io.github.leylaragg.letool.web.support.WebPathMatcher;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * 将普通 JSON Controller 返回值包装为统一的 {@link R} 响应。
 *
 * <p>包装器尊重 Spring MVC 的内容协商结果，只处理 JSON 和 {@code application/*+json}。
 * 下载、原始文本、字节、流式响应、SSE、Spring 错误协议以及显式排除的接口均保持原样，
 * 避免统一协议破坏 HTTP 原生语义。</p>
 */
@RestControllerAdvice
public class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    /** 应用内排除路径匹配器。 */
    private final WebPathMatcher excludePathMatcher;

    /**
     * 创建统一响应包装器。
     *
     * @param excludePaths 不参与包装的应用内路径表达式
     */
    public ResponseWrapperAdvice(List<String> excludePaths) {
        this.excludePathMatcher = new WebPathMatcher(excludePaths);
    }

    /**
     * 判断声明的返回类型和 Controller 注解是否允许进入包装阶段。
     *
     * @param returnType Controller 方法返回值描述
     * @param converterType Spring 选择的消息转换器类型
     * @return 允许继续检查实际响应体时返回 {@code true}
     */
    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> declaredType = returnType.getParameterType();
        return !isExcludedDeclaredType(declaredType)
                && !returnType.hasMethodAnnotation(ExcludeWrapper.class)
                && !AnnotatedElementUtils.hasAnnotation(
                        returnType.getContainingClass(),
                        ExcludeWrapper.class);
    }

    /**
     * 根据请求路径、HTTP 语义、媒体类型和实际响应体决定是否包装。
     *
     * @param body Controller 原始返回值
     * @param returnType Controller 方法返回值描述
     * @param selectedContentType 最终响应媒体类型
     * @param selectedConverterType Spring 选择的消息转换器类型
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 原始响应体或包装后的统一响应
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (isExcludedRequest(request, response)
                || !isJson(selectedContentType)
                || isExcludedBody(body)) {
            return body;
        }
        return R.ok(body);
    }

    /**
     * 判断声明返回类型是否属于不能包装的 HTTP 响应。
     *
     * @param declaredType 声明返回类型
     * @return 不能包装时返回 {@code true}
     */
    private static boolean isExcludedDeclaredType(Class<?> declaredType) {
        return R.class.isAssignableFrom(declaredType)
                || CharSequence.class.isAssignableFrom(declaredType)
                || byte[].class == declaredType
                || Resource.class.isAssignableFrom(declaredType)
                || InputStreamSource.class.isAssignableFrom(declaredType)
                || StreamingResponseBody.class.isAssignableFrom(declaredType)
                || ResponseBodyEmitter.class.isAssignableFrom(declaredType)
                || SseEmitter.class.isAssignableFrom(declaredType)
                || ProblemDetail.class.isAssignableFrom(declaredType);
    }

    /**
     * 判断实际响应体是否属于不能包装的特殊类型。
     *
     * @param body 实际响应体
     * @return 不能包装时返回 {@code true}
     */
    private static boolean isExcludedBody(Object body) {
        return body instanceof R<?>
                || body instanceof CharSequence
                || body instanceof byte[]
                || body instanceof Resource
                || body instanceof InputStreamSource
                || body instanceof StreamingResponseBody
                || body instanceof ResponseBodyEmitter
                || body instanceof SseEmitter
                || body instanceof ProblemDetail;
    }

    /**
     * 判断当前请求和响应是否要求保持无响应体或原始响应。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @return 需要排除包装时返回 {@code true}
     */
    private boolean isExcludedRequest(ServerHttpRequest request, ServerHttpResponse response) {
        if (request.getMethod() == HttpMethod.HEAD) {
            return true;
        }
        if (response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus() == HttpStatus.NO_CONTENT.value()) {
            return true;
        }
        return request instanceof ServletServerHttpRequest servletRequest
                && excludePathMatcher.matches(servletRequest.getServletRequest());
    }

    /**
     * 判断最终媒体类型是否属于 JSON 协议。
     *
     * @param mediaType 最终响应媒体类型
     * @return JSON 或 {@code +json} 时返回 {@code true}
     */
    private static boolean isJson(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        String subtype = mediaType.getSubtype();
        return "json".equalsIgnoreCase(subtype)
                || subtype.toLowerCase(java.util.Locale.ROOT).endsWith("+json");
    }
}
