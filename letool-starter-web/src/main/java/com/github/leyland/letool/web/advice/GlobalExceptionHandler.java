package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.exception.core.SystemException;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.web.code.WebErrorCode;
import com.github.leyland.letool.web.exception.RequestBodyTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将应用异常和 Spring MVC 异常统一转换为 {@link R} 响应协议。
 *
 * <p>该处理器继承 Spring 的 {@link ResponseEntityExceptionHandler}，复用框架对 MVC
 * 异常的分派规则，再将其转换为 Letool 稳定错误码。4xx 响应不回显底层异常文本，
 * 5xx 响应只返回安全通用消息，同时在服务端日志保留完整 Throwable。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 异常协议日志。 */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 异常模块国际化消息解析器。 */
    private final MessageResolver messageResolver;

    /**
     * 创建 HTTP 异常适配器。
     *
     * @param messageResolver 用于生成国际化安全消息的必填解析器
     * @throws NullPointerException 当解析器为 {@code null} 时抛出
     */
    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = Objects.requireNonNull(messageResolver, "messageResolver");
    }

    /**
     * 将预期业务拒绝转换为 HTTP 400。
     *
     * @param exception 带领域错误码的业务异常
     * @return 保留领域错误码和国际化消息的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception: [{}] {}", exception.getCode(), exception.getFallbackMessage());
        return codedResponse(exception, HttpStatus.BAD_REQUEST);
    }

    /**
     * 将系统异常转换为安全的 HTTP 500，并保留完整服务端日志。
     *
     * @param exception 带系统错误码的异常
     * @return 保留系统错误码和安全国际化消息的失败响应
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<R<Void>> handleSystemException(SystemException exception) {
        log.error("System exception: [{}] {}", exception.getCode(), exception.getFallbackMessage(), exception);
        return codedResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理未归类为业务或系统异常的带码异常。
     *
     * @param exception 应用自定义带码异常
     * @return HTTP 500 带码失败响应
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<R<Void>> handleBaseException(BaseException exception) {
        log.error("Coded exception: [{}] {}", exception.getCode(), exception.getFallbackMessage(), exception);
        return codedResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 将请求体缓存越界转换为 HTTP 413。
     *
     * @param exception 请求体过大异常
     * @return HTTP 413 统一失败响应
     */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<R<Void>> handleRequestBodyTooLarge(RequestBodyTooLargeException exception) {
        log.warn(
                "Request body exceeds repeatable cache limit: actual={} bytes, max={} bytes",
                exception.getActualSize(),
                exception.getMaxSize());
        return response(WebErrorCode.REQUEST_BODY_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * 将显式非法参数转换为安全的 HTTP 400。
     *
     * @param exception 非法参数异常
     * @return HTTP 400 统一失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("Illegal argument: {}", exception.getClass().getName());
        return response(WebErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理未被 Spring MVC 基类分派的绑定异常。
     *
     * @param exception 请求绑定异常
     * @return HTTP 400 参数校验失败响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Map<String, String>>> handleBindException(BindException exception) {
        log.warn("Request binding validation failed");
        return validationResponse(exception.getBindingResult(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 将未知异常转换为固定安全的 HTTP 500。
     *
     * @param exception 未分类异常
     * @return HTTP 500 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGenericException(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(WebErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理请求方法不支持异常。
     *
     * @param exception 请求方法不支持异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 405 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("HTTP method is not supported");
        return responseObject(WebErrorCode.METHOD_NOT_SUPPORTED, statusCode, headers, null);
    }

    /**
     * 处理请求媒体类型不支持异常。
     *
     * @param exception 请求媒体类型不支持异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 415 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("HTTP request media type is not supported");
        return responseObject(WebErrorCode.MEDIA_TYPE_NOT_SUPPORTED, statusCode, headers, null);
    }

    /**
     * 处理无法生成客户端接受媒体类型的异常。
     *
     * @param exception 响应媒体类型不可接受异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 406 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("No acceptable HTTP response media type");
        return responseObject(WebErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE, statusCode, headers, null);
    }

    /**
     * 处理缺少查询参数异常。
     *
     * @param exception 缺少请求参数异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Required request parameter is missing");
        return responseObject(WebErrorCode.MISSING_PARAMETER, statusCode, headers, null);
    }

    /**
     * 处理缺少 multipart 请求部分异常。
     *
     * @param exception 缺少请求部分异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Required multipart request part is missing");
        return responseObject(WebErrorCode.MISSING_PARAMETER, statusCode, headers, null);
    }

    /**
     * 处理缺少请求头等 Servlet 绑定异常。
     *
     * @param exception Servlet 请求绑定异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Servlet request binding failed");
        return responseObject(WebErrorCode.MISSING_PARAMETER, statusCode, headers, null);
    }

    /**
     * 处理 Controller 方法参数校验异常。
     *
     * @param exception 方法参数校验异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 带稳定字段消息的失败响应
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Method argument validation failed");
        R<Map<String, String>> body = validationBody(exception.getBindingResult());
        return new ResponseEntity<>(body, headers, statusCode);
    }

    /**
     * 处理方法级参数约束校验异常。
     *
     * @param exception 方法级校验异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Handler method validation failed");
        return responseObject(WebErrorCode.VALIDATION_FAILED, statusCode, headers, null);
    }

    /**
     * 处理请求参数类型转换失败。
     *
     * @param exception 类型转换异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Request value type mismatch");
        return responseObject(WebErrorCode.TYPE_MISMATCH, statusCode, headers, null);
    }

    /**
     * 处理请求体无法解析异常。
     *
     * @param exception 请求体不可读异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 400 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("HTTP request body is not readable");
        return responseObject(WebErrorCode.MESSAGE_NOT_READABLE, statusCode, headers, null);
    }

    /**
     * 处理请求处理器不存在异常。
     *
     * @param exception 无处理器异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 404 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("HTTP request handler was not found");
        return responseObject(WebErrorCode.RESOURCE_NOT_FOUND, statusCode, headers, null);
    }

    /**
     * 处理静态资源不存在异常。
     *
     * @param exception 静态资源不存在异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 404 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("HTTP resource was not found");
        return responseObject(WebErrorCode.RESOURCE_NOT_FOUND, statusCode, headers, null);
    }

    /**
     * 处理上传大小超限异常。
     *
     * @param exception 上传大小超限异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return HTTP 413 统一失败响应
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Multipart upload exceeds configured limit");
        return responseObject(WebErrorCode.REQUEST_BODY_TOO_LARGE, statusCode, headers, null);
    }

    /**
     * 处理 Spring 未单独归类的标准错误响应异常。
     *
     * @param exception Spring 标准错误响应异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return 保留合法错误状态的安全统一响应
     */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
            ErrorResponseException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        return categoryResponse(exception, headers, statusCode);
    }

    /**
     * 处理服务端无法写出响应体的异常。
     *
     * @param exception 响应体写出异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return 安全服务端错误响应
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(
            HttpMessageNotWritableException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.error("HTTP response body is not writable", exception);
        return responseObject(WebErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, headers, null);
    }

    /**
     * 统一处理 Spring 基类尚未被具体方法覆盖的异常。
     *
     * @param exception 原始异常
     * @param body Spring 原始响应体
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @param request 当前 Web 请求
     * @return 安全统一响应
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        return categoryResponse(exception, headers, statusCode);
    }

    /**
     * 根据 HTTP 状态类别创建不泄露异常细节的响应。
     *
     * @param exception 原始异常
     * @param headers Spring 生成的响应头
     * @param statusCode Spring 计算的 HTTP 状态
     * @return 4xx 客户端错误或安全服务端错误响应
     */
    private ResponseEntity<Object> categoryResponse(
            Exception exception,
            HttpHeaders headers,
            HttpStatusCode statusCode) {
        if (statusCode.is4xxClientError()) {
            log.warn("Spring MVC client error: status={}, type={}",
                    statusCode.value(), exception.getClass().getName());
            return responseObject(WebErrorCode.CLIENT_ERROR, statusCode, headers, null);
        }
        HttpStatusCode safeStatus = statusCode.is5xxServerError()
                ? statusCode
                : HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Spring MVC server error: status={}", safeStatus.value(), exception);
        return responseObject(WebErrorCode.SYSTEM_ERROR, safeStatus, headers, null);
    }

    /**
     * 创建保留应用异常错误码和国际化消息的响应。
     *
     * @param exception 带码异常
     * @param status HTTP 状态
     * @return 带码统一失败响应
     */
    private ResponseEntity<R<Void>> codedResponse(BaseException exception, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(R.fail(exception.getCode(), messageResolver.resolve(exception)));
    }

    /**
     * 创建不带附加数据的 Web 模块失败响应。
     *
     * @param errorCode Web 模块错误码
     * @param status HTTP 状态
     * @return 统一失败响应
     */
    private ResponseEntity<R<Void>> response(WebErrorCode errorCode, HttpStatus status) {
        return ResponseEntity.status(status).body(failure(errorCode, null));
    }

    /**
     * 创建 Spring MVC 基类需要的对象类型失败响应。
     *
     * @param errorCode Web 模块错误码
     * @param statusCode HTTP 状态
     * @param headers 响应头
     * @param data 可选附加数据
     * @return 对象类型统一失败响应
     */
    private ResponseEntity<Object> responseObject(
            WebErrorCode errorCode,
            HttpStatusCode statusCode,
            HttpHeaders headers,
            Object data) {
        return new ResponseEntity<>(failure(errorCode, data), headers, statusCode);
    }

    /**
     * 创建字段校验失败响应。
     *
     * @param bindingResult Spring 绑定结果
     * @param status HTTP 状态
     * @return 带稳定字段消息的失败响应
     */
    private ResponseEntity<R<Map<String, String>>> validationResponse(
            BindingResult bindingResult,
            HttpStatus status) {
        return ResponseEntity.status(status).body(validationBody(bindingResult));
    }

    /**
     * 创建字段校验失败响应体，不包含被拒绝的原始值。
     *
     * @param bindingResult Spring 绑定结果
     * @return 带有序字段消息的失败响应体
     */
    private R<Map<String, String>> validationBody(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error -> errors.putIfAbsent(
                error.getField(),
                safeValidationMessage(error.getDefaultMessage())));
        bindingResult.getGlobalErrors().forEach(error -> errors.putIfAbsent(
                error.getObjectName(),
                safeValidationMessage(error.getDefaultMessage())));
        return failure(WebErrorCode.VALIDATION_FAILED, errors);
    }

    /**
     * 将可空校验消息转换为安全展示文本。
     *
     * @param message Spring 校验消息
     * @return 非空白安全消息
     */
    private String safeValidationMessage(String message) {
        return message == null || message.isBlank()
                ? resolve(WebErrorCode.VALIDATION_FAILED)
                : message;
    }

    /**
     * 创建 Web 模块失败响应体。
     *
     * @param errorCode Web 模块错误码
     * @param data 可选附加数据
     * @param <T> 附加数据类型
     * @return 统一失败响应体
     */
    private <T> R<T> failure(WebErrorCode errorCode, T data) {
        return R.fail(errorCode.getCode(), resolve(errorCode), data);
    }

    /**
     * 按当前请求语言环境解析 Web 模块消息。
     *
     * @param errorCode Web 模块错误码
     * @return 国际化消息或安全默认消息
     */
    private String resolve(WebErrorCode errorCode) {
        return messageResolver.resolve(errorCode, LocaleContextHolder.getLocale());
    }
}
