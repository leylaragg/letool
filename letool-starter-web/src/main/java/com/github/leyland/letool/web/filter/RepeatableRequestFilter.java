package com.github.leyland.letool.web.filter;

import com.github.leyland.letool.web.exception.RequestBodyTooLargeException;
import com.github.leyland.letool.web.support.WebPathMatcher;
import com.github.leyland.letool.web.wrapper.RepeatableRequestWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 为明确允许的文本请求提供受限请求体重复读取能力。
 *
 * <p>过滤器只处理配置允许的 JSON、XML 或文本媒体类型，并始终避让 multipart、表单和
 * 二进制请求。请求体缓存超过限制时，异常会主动委托 Spring MVC 异常解析器，以复用
 * Web 模块统一的 HTTP 413 响应协议。</p>
 */
public final class RepeatableRequestFilter implements Filter {

    /** 可配置缓存大小的生产硬上限，防止误配置造成堆内存风险。 */
    public static final long MAX_CACHE_SIZE = 16L * 1024 * 1024;

    /** 允许缓存的最大请求体字节数。 */
    private final long maxBodySize;

    /** 应用内排除路径匹配器。 */
    private final WebPathMatcher excludePathMatcher;

    /** 允许缓存的媒体类型表达式。 */
    private final List<MediaType> includeMediaTypes;

    /** 用于输出统一 413 协议的 Spring MVC 异常解析器。 */
    private final HandlerExceptionResolver exceptionResolver;

    /**
     * 创建可重复读请求体过滤器。
     *
     * @param maxBodySize 最大缓存字节数，必须在 1 到 16 MiB 之间
     * @param excludePaths 不缓存请求体的应用内路径表达式
     * @param includeMediaTypes 允许缓存的媒体类型表达式
     * @param exceptionResolver 非空 Spring MVC 异常解析器
     * @throws IllegalArgumentException 当大小或媒体类型配置不合法时抛出
     * @throws NullPointerException 当必要参数为 {@code null} 时抛出
     */
    public RepeatableRequestFilter(
            long maxBodySize,
            List<String> excludePaths,
            List<String> includeMediaTypes,
            HandlerExceptionResolver exceptionResolver) {
        if (maxBodySize <= 0 || maxBodySize > MAX_CACHE_SIZE) {
            throw new IllegalArgumentException("maxBodySize must be between 1 byte and 16 MiB");
        }
        this.maxBodySize = maxBodySize;
        this.excludePathMatcher = new WebPathMatcher(excludePaths);
        Objects.requireNonNull(includeMediaTypes, "includeMediaTypes");
        this.includeMediaTypes = includeMediaTypes.stream()
                .map(RepeatableRequestFilter::parseMediaType)
                .toList();
        this.exceptionResolver = Objects.requireNonNull(exceptionResolver, "exceptionResolver");
    }

    /**
     * 按路径、请求体、媒体类型和重复包装状态决定是否缓存请求体。
     *
     * @param request 当前 Servlet 请求
     * @param response 当前 Servlet 响应
     * @param chain 下游过滤器链
     * @throws IOException 读取请求体或下游处理失败时抛出
     * @throws ServletException 下游处理失败或异常解析器拒绝处理越界异常时抛出
     */
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || !shouldWrap(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            chain.doFilter(new RepeatableRequestWrapper(httpRequest, maxBodySize), response);
        } catch (RequestBodyTooLargeException exception) {
            ModelAndView resolved = exceptionResolver.resolveException(
                    httpRequest,
                    httpResponse,
                    null,
                    exception);
            if (resolved == null) {
                throw new ServletException("请求体超过重复读缓存上限且未被异常解析器处理", exception);
            }
        }
    }

    /**
     * 判断当前 HTTP 请求是否适合进入内存重复读缓存。
     *
     * @param request 当前 HTTP 请求
     * @return 满足全部安全边界时返回 {@code true}
     */
    private boolean shouldWrap(HttpServletRequest request) {
        if (request instanceof RepeatableRequestWrapper
                || excludePathMatcher.matches(request)
                || !hasRequestBody(request)) {
            return false;
        }
        MediaType contentType = requestMediaType(request);
        if (contentType == null || isAlwaysExcluded(contentType)) {
            return false;
        }
        return includeMediaTypes.stream()
                .anyMatch(configured -> configured.isCompatibleWith(contentType));
    }

    /**
     * 根据声明长度和 HTTP 方法判断请求是否可能携带消息体。
     *
     * @param request 当前 HTTP 请求
     * @return 需要读取消息体时返回 {@code true}
     */
    private static boolean hasRequestBody(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength == 0) {
            return false;
        }
        if (contentLength > 0) {
            return true;
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    /**
     * 解析请求 Content-Type，非法值交给后续 Spring MVC 处理。
     *
     * @param request 当前 HTTP 请求
     * @return 已解析媒体类型；缺失或非法时返回 {@code null}
     */
    private static MediaType requestMediaType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ignored) {
            return null;
        }
    }

    /**
     * 判断媒体类型是否属于禁止进入内存缓存的上传、表单或二进制类型。
     *
     * @param contentType 请求媒体类型
     * @return 必须绕过缓存时返回 {@code true}
     */
    private static boolean isAlwaysExcluded(MediaType contentType) {
        return "multipart".equalsIgnoreCase(contentType.getType())
                || MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)
                || MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(contentType);
    }

    /**
     * 解析并校验配置媒体类型表达式。
     *
     * @param value 媒体类型表达式
     * @return 已解析媒体类型
     * @throws IllegalArgumentException 当表达式为空或语法非法时抛出
     */
    private static MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("includeMediaTypes must not contain blank values");
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (InvalidMediaTypeException exception) {
            throw new IllegalArgumentException("非法请求体媒体类型表达式: " + value, exception);
        }
    }
}
