package io.github.leylaragg.letool.web.advice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.BusinessException;
import io.github.leylaragg.letool.exception.core.SystemException;
import io.github.leylaragg.letool.exception.message.SpringMessageResolver;
import io.github.leylaragg.letool.tool.model.R;
import io.github.leylaragg.letool.web.exception.RequestBodyTooLargeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Web 异常协议的关键状态、错误码和安全边界测试。
 */
class GlobalExceptionHandlerTest {

    /** 测试消息源。 */
    private StaticMessageSource messageSource;

    /** 被测全局异常处理器。 */
    private GlobalExceptionHandler handler;

    /** 异常处理器日志对象。 */
    private Logger handlerLogger;

    /** 测试日志收集器。 */
    private ListAppender<ILoggingEvent> logAppender;

    /** 测试前的日志级别。 */
    private Level originalLogLevel;

    /** 测试前的日志传递设置。 */
    private boolean originalAdditive;

    /**
     * 初始化国际化解析器和隔离的日志收集器。
     */
    @BeforeEach
    void setUp() {
        messageSource = new StaticMessageSource();
        handler = new GlobalExceptionHandler(
                new SpringMessageResolver(null, messageSource, Locale.SIMPLIFIED_CHINESE));

        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        originalLogLevel = handlerLogger.getLevel();
        originalAdditive = handlerLogger.isAdditive();
        logAppender = new ListAppender<>();
        logAppender.setContext(handlerLogger.getLoggerContext());
        logAppender.start();
        handlerLogger.setLevel(Level.TRACE);
        handlerLogger.setAdditive(false);
        handlerLogger.addAppender(logAppender);
    }

    /**
     * 恢复语言环境和日志对象，避免测试间互相污染。
     */
    @AfterEach
    void restoreTestContext() {
        LocaleContextHolder.resetLocaleContext();
        if (handlerLogger != null && logAppender != null) {
            handlerLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        if (handlerLogger != null) {
            handlerLogger.setLevel(originalLogLevel);
            handlerLogger.setAdditive(originalAdditive);
        }
    }

    /**
     * 验证业务异常保留领域错误码、国际化消息和 HTTP 400。
     */
    @Test
    void shouldReturnLocalizedBusinessFailure() {
        messageSource.addMessage("BIZ_001", Locale.ENGLISH, "Order {0} not found");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BusinessException exception = BusinessException.of(
                ErrorCode.of("BIZ_001", "订单 {0} 不存在"),
                "42");

        ResponseEntity<R<Void>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("BIZ_001");
        assertThat(response.getBody().getMessage()).isEqualTo("Order 42 not found");
    }

    /**
     * 验证系统异常只向客户端返回安全消息，同时在服务端日志保留完整原因链。
     */
    @Test
    void shouldHideSystemCauseFromResponseAndKeepItInLogs() {
        messageSource.addMessage("SYS_SAFE", Locale.ENGLISH, "Service unavailable");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        SystemException exception = SystemException.causedBy(
                ErrorCode.of("SYS_SAFE", "服务暂不可用"),
                new IllegalStateException("redis password=secret"));

        ResponseEntity<R<Void>> response = handler.handleSystemException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Service unavailable")
                .doesNotContain("password", "secret", "redis");
        assertThat(logAppender.list).singleElement().satisfies(event ->
                assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                        .contains("redis password=secret"));
    }

    /**
     * 验证常见 Spring MVC 异常映射为稳定错误码和正确 HTTP 状态。
     *
     * @throws Exception Spring 异常分派失败时抛出
     */
    @Test
    void shouldMapCommonMvcExceptionsToStableProtocol() throws Exception {
        ServletWebRequest request = webRequest();

        ResponseEntity<Object> unreadable = handler.handleException(
                new HttpMessageNotReadableException(
                        "password=secret",
                        new MockHttpInputMessage(new byte[0])),
                request);
        assertFailure(unreadable, HttpStatus.BAD_REQUEST, "WEB_400_004", "password", "secret");

        ResponseEntity<Object> unsupportedMethod = handler.handleException(
                new HttpRequestMethodNotSupportedException("TRACE"),
                request);
        assertFailure(unsupportedMethod, HttpStatus.METHOD_NOT_ALLOWED, "WEB_405_001", "TRACE");
    }

    /**
     * 验证字段校验只返回稳定字段消息，不返回被拒绝的原始值。
     *
     * @throws Exception Spring 异常分派或反射失败时抛出
     */
    @Test
    void shouldReturnStableValidationDetailsWithoutRejectedValue() throws Exception {
        ValidationRequest target = new ValidationRequest("password=secret");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.rejectValue("name", "NotBlank", "名称不能为空");
        Method method = ValidationTarget.class.getDeclaredMethod("create", ValidationRequest.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0),
                bindingResult);

        ResponseEntity<Object> response = handler.handleException(exception, webRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        R<?> body = responseBody(response);
        assertThat(body.getCode()).isEqualTo("WEB_400_001");
        assertThat(body.getData()).isEqualTo(Map.of("name", "名称不能为空"));
        assertThat(String.valueOf(body.getData())).doesNotContain("password=secret");
    }

    /**
     * 验证请求体缓存越界返回 HTTP 413，而不是普通业务错误的 HTTP 400。
     */
    @Test
    void shouldMapRequestBodyLimitToPayloadTooLarge() {
        ResponseEntity<R<Void>> response = handler.handleRequestBodyTooLarge(
                new RequestBodyTooLargeException(1025, 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WEB_413_001");
    }

    /**
     * 验证未单独分类的 Spring 4xx 状态保留 HTTP 状态，但不回显原始原因。
     *
     * @throws Exception Spring 异常分派失败时抛出
     */
    @Test
    void shouldPreserveUnclassifiedClientStatusWithoutLeakingReason() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new ResponseStatusException(HttpStatus.CONFLICT, "database password=secret"),
                webRequest());

        assertFailure(response, HttpStatus.CONFLICT, "WEB_4XX_001", "password", "secret");
    }

    /**
     * 验证未知异常固定返回安全的 Web 系统错误协议。
     */
    @Test
    void shouldReturnSafeGenericFailure() {
        ResponseEntity<R<Void>> response = handler.handleGenericException(
                new Exception("database password=secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WEB_500_001");
        assertThat(response.getBody().getMessage()).doesNotContain("password", "secret", "database");
    }

    /**
     * 验证异常处理器拒绝空国际化解析器。
     */
    @Test
    void shouldRejectNullMessageResolver() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GlobalExceptionHandler(null))
                .withMessage("messageResolver");
    }

    /**
     * 创建当前测试使用的 Servlet Web 请求。
     *
     * @return Servlet Web 请求
     */
    private ServletWebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }

    /**
     * 断言统一失败响应的状态、错误码和敏感文本边界。
     *
     * @param response Spring MVC 响应
     * @param status 预期 HTTP 状态
     * @param code 预期稳定错误码
     * @param forbiddenTexts 响应中禁止出现的文本
     */
    private void assertFailure(
            ResponseEntity<Object> response,
            HttpStatus status,
            String code,
            String... forbiddenTexts) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        R<?> body = responseBody(response);
        assertThat(body.getCode()).isEqualTo(code);
        assertThat(body.getMessage()).doesNotContain(forbiddenTexts);
    }

    /**
     * 获取并校验统一响应体类型。
     *
     * @param response Spring MVC 响应
     * @return 统一响应体
     */
    private R<?> responseBody(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(R.class);
        return (R<?>) response.getBody();
    }

    /**
     * 参数校验测试对象。
     *
     * @param name 名称
     */
    private record ValidationRequest(String name) {
    }

    /**
     * 为构造方法参数描述提供的测试目标。
     */
    private static final class ValidationTarget {

        /**
         * 接收校验请求。
         *
         * @param request 校验请求
         */
        void create(ValidationRequest request) {
        }
    }
}
