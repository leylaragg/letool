package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.web.annotation.ExcludeWrapper;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应体统一包装 —— 将 Controller 返回的非 {@link R} 类型自动包装为 {@code R<T>}.
 *
 * <p>以下情况不包装：</p>
 * <ul>
 *   <li>返回值已经是 {@link R} 类型</li>
 *   <li>返回值是 {@link String} 类型（避免转换问题）</li>
 *   <li>返回值是 {@link Resource} 类型（文件下载）</li>
 *   <li>方法或类标注了 {@link ExcludeWrapper @ExcludeWrapper} 注解</li>
 * </ul>
 */
@RestControllerAdvice
public class ResponseWrapperAdvice implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 已经是 R 类型 → 不包装
        if (R.class.isAssignableFrom(returnType.getParameterType())) return false;
        // String 类型 → 不包装（避免 MessageConverter 混乱）
        if (String.class.isAssignableFrom(returnType.getParameterType())) return false;
        // Resource 类型（文件下载）→ 不包装
        if (Resource.class.isAssignableFrom(returnType.getParameterType())) return false;
        // 标注了 @ExcludeWrapper → 不包装
        if (returnType.hasMethodAnnotation(ExcludeWrapper.class)) return false;
        if (returnType.getContainingClass() != null
                && returnType.getContainingClass().isAnnotationPresent(ExcludeWrapper.class)) return false;
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return R.ok(body);
    }
}
