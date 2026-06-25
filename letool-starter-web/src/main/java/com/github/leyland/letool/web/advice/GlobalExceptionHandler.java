package com.github.leyland.letool.web.advice;

import com.github.leyland.letool.tool.exception.BusinessException;
import com.github.leyland.letool.tool.exception.LetoolException;
import com.github.leyland.letool.tool.exception.SystemException;
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

/**
 * 全局异常处理器 —— 将所有异常统一转换为 {@link R} 格式返回.
 *
 * <h3>异常 → HTTP 状态码映射</h3>
 * <ul>
 *   <li>{@link BusinessException} → 400 Bad Request</li>
 *   <li>{@link SystemException} → 500 Internal Server Error</li>
 *   <li>{@link LetoolException} → 500 Internal Server Error</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request（提取字段错误）</li>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
 *   <li>{@link Exception} → 500 Internal Server Error（兜底）</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: [{}] {}", e.getErrorCode(), e.getMessage());
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleSystemException(SystemException e) {
        log.error("System exception: [{}] {}", e.getErrorCode(), e.getMessage(), e);
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(LetoolException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleLetoolException(LetoolException e) {
        log.error("Letool exception: [{}] {}", e.getErrorCode(), e.getMessage(), e);
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {}", fieldErrors);
        return R.fail("VALID_001", "参数校验失败", fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return R.fail("ARG_001", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleGenericException(Exception e) {
        log.error("Unhandled exception", e);
        return R.fail("SYS_001", "系统内部错误，请稍后重试");
    }
}
