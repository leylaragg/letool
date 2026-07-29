package com.github.leyland.letool.web.advice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.leyland.letool.exception.code.ErrorCode;
import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.exception.core.SystemException;
import com.github.leyland.letool.exception.message.SpringMessageResolver;
import com.github.leyland.letool.tool.model.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GlobalExceptionHandlerTest {

    private StaticMessageSource messageSource;
    private GlobalExceptionHandler handler;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logAppender;
    private Level originalLogLevel;
    private boolean originalAdditive;

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

    @Test
    void businessExceptionShouldReturnLocalizedMessageAndCode() throws Exception {
        messageSource.addMessage("BIZ_001", Locale.ENGLISH, "Order {0} not found");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BusinessException exception = BusinessException.of(
                ErrorCode.of("BIZ_001", "订单 {0} 不存在"),
                "42");

        R<Void> result = handler.handleBusinessException(exception);

        assertThat(result.getCode()).isEqualTo("BIZ_001");
        assertThat(result.getMessage()).isEqualTo("Order 42 not found");
        assertThat(responseStatusFor("handleBusinessException", BusinessException.class).value())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void systemExceptionShouldReturnLocalizedSafeMessageWithoutCauseDetails() throws Exception {
        messageSource.addMessage(
                "SYS_SAFE",
                Locale.ENGLISH,
                "The service is temporarily unavailable");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        SystemException exception = SystemException.causedBy(
                ErrorCode.of("SYS_SAFE", "服务暂时不可用"),
                new IllegalStateException("redis password=secret"));

        R<Void> result = handler.handleSystemException(exception);

        assertThat(result.getCode()).isEqualTo("SYS_SAFE");
        assertThat(result.getMessage())
                .isEqualTo("The service is temporarily unavailable")
                .doesNotContain("password", "secret", "redis");
        assertThat(responseStatusFor("handleSystemException", SystemException.class).value())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    .contains(SystemException.class.getName())
                    .contains("redis password=secret");
        });
    }

    @Test
    void customBaseExceptionShouldUseGenericCodedHandlerAtInternalServerError() throws Exception {
        messageSource.addMessage("CUSTOM_001", Locale.ENGLISH, "Localized coded failure");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BaseException exception = new CustomCodedException(
                ErrorCode.of("CUSTOM_001", "Coded failure"));

        R<Void> result = handler.handleBaseException(exception);
        ResponseStatus responseStatus = GlobalExceptionHandler.class
                .getMethod("handleBaseException", BaseException.class)
                .getAnnotation(ResponseStatus.class);

        assertThat(result.getCode()).isEqualTo("CUSTOM_001");
        assertThat(result.getMessage()).isEqualTo("Localized coded failure");
        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void customBusinessMessageShouldBypassMessageSource() {
        messageSource.addMessage("BIZ_CUSTOM", Locale.ENGLISH, "Translated message");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BusinessException exception = BusinessException.custom(
                ErrorCode.of("BIZ_CUSTOM", "Fallback message"),
                "Exact custom message");

        R<Void> result = handler.handleBusinessException(exception);

        assertThat(result.getCode()).isEqualTo("BIZ_CUSTOM");
        assertThat(result.getMessage()).isEqualTo("Exact custom message");
    }

    @Test
    void illegalArgumentExceptionShouldReturnSafeMessageWithoutCauseDetails() {
        IllegalArgumentException exception = new IllegalArgumentException(
                new IllegalStateException("password=secret"));

        R<Void> result = handler.handleIllegalArgumentException(exception);

        assertThat(result.getCode()).isEqualTo("ARG_001");
        assertThat(result.getMessage())
                .isEqualTo("参数不合法")
                .doesNotContain("password", "secret", "IllegalStateException");
    }

    @Test
    void illegalArgumentExceptionShouldRetainThrowableInLogs() {
        IllegalArgumentException exception = new IllegalArgumentException(
                new IllegalStateException("password=secret"));

        handler.handleIllegalArgumentException(exception);

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    .contains(IllegalArgumentException.class.getName())
                    .contains(IllegalStateException.class.getName())
                    .contains("password=secret");
        });
    }

    @Test
    void genericExceptionShouldReturnSafeGenericMessage() {
        Exception exception = new Exception("未知错误");

        R<Void> result = handler.handleGenericException(exception);

        assertThat(result.getCode()).isEqualTo("SYS_001");
        assertThat(result.getMessage()).isEqualTo("系统内部错误，请稍后重试");
    }

    @Test
    void constructorShouldRejectNullMessageResolver() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GlobalExceptionHandler(null))
                .withMessage("messageResolver");
    }

    private ResponseStatus responseStatusFor(String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        ResponseStatus responseStatus = GlobalExceptionHandler.class
                .getMethod(methodName, parameterType)
                .getAnnotation(ResponseStatus.class);
        assertThat(responseStatus).isNotNull();
        return responseStatus;
    }

    private static final class CustomCodedException extends BaseException {

        private CustomCodedException(ErrorCode errorCode) {
            super(errorCode, null, null, null);
        }
    }
}
