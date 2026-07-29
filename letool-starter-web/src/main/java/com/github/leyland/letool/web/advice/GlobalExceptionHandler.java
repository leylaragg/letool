package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.exception.core.SystemException;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.tool.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将应用异常转换为统一的 {@link R} 响应协议。
 *
 * <p>带错误码异常按语义映射状态码：预期的 {@link BusinessException 业务异常} 返回
 * HTTP 400；{@link SystemException 系统异常} 和其他
 * {@link BaseException 带错误码异常} 返回 HTTP 500。
 * 参数校验和非法参数异常仍属于客户端错误，未分类异常则返回安全的通用服务端错误。</p>
 *
 * <p>带错误码响应通过注入的 {@link MessageResolver} 在 HTTP 边界进行国际化。
 * 日志使用异常的稳定默认消息，不依赖请求国际化文本。服务端异常会连同异常对象一起记录，
 * 以保留异常原因和堆栈，但这些信息不会复制到响应中。该边界用于避免基础设施细节和敏感信息泄露给客户端。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messageResolver;

    /**
     * 创建 HTTP 异常适配器。
     *
     * @param messageResolver 用于生成国际化带码响应的必填解析器
     * @throws NullPointerException 当 {@code messageResolver} 为 {@code null} 时抛出
     */
    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = Objects.requireNonNull(messageResolver, "messageResolver");
    }

    /**
     * 将预期业务拒绝转换为国际化客户端错误。
     *
     * @param exception 带错误码的业务拒绝
     * @return 包含稳定错误码和请求国际化消息的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException exception) {
        log.warn(
                "Business exception: [{}] {}",
                exception.getCode(),
                exception.getFallbackMessage());
        return R.fail(exception.getCode(), messageResolver.resolve(exception));
    }

    /**
     * 安全转换技术故障，并在日志中保留完整异常信息。
     *
     * @param exception 带错误码的系统故障
     * @return 包含稳定错误码和请求国际化安全消息的失败响应
     */
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleSystemException(SystemException exception) {
        log.error(
                "System exception: [{}] {}",
                exception.getCode(),
                exception.getFallbackMessage(),
                exception);
        return R.fail(exception.getCode(), messageResolver.resolve(exception));
    }

    /**
     * 处理既不属于业务异常也不属于系统异常的自定义带码异常。
     *
     * @param exception 应用自定义的带码异常
     * @return 包含稳定错误码和请求国际化消息的失败响应
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleBaseException(BaseException exception) {
        log.error(
                "Coded exception: [{}] {}",
                exception.getCode(),
                exception.getFallbackMessage(),
                exception);
        return R.fail(exception.getCode(), messageResolver.resolve(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Map<String, String>> handleValidationException(
            MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", fieldErrors);
        return R.fail("VALID_001", "参数校验失败", fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("Illegal argument", exception);
        return R.fail("ARG_001", "参数不合法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleGenericException(Exception exception) {
        log.error("Unhandled exception", exception);
        return R.fail("SYS_001", "系统内部错误，请稍后重试");
    }
}
