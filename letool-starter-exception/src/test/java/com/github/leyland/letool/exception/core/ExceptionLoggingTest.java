package com.github.leyland.letool.exception.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.leyland.letool.exception.code.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionLoggingTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;
    private boolean originalAdditive;

    @BeforeEach
    void attachListAppender() {
        logger = (Logger) LoggerFactory.getLogger(ExceptionLoggingTest.class.getName() + ".capture");
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();

        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();

        logger.setLevel(Level.ERROR);
        logger.setAdditive(false);
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLoggerState() {
        if (logger != null && appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
        if (logger != null) {
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }

    @Test
    void logsSystemExceptionWithCauseInThrowableRendering() {
        ErrorCode code = ErrorCode.of("SYS_TEST", "锁后端不可用：{0}");
        IllegalStateException cause = new IllegalStateException("redis down");
        SystemException exception = SystemException.causedBy(code, cause, "redis");

        logger.error("operation failed", exception);

        assertThat(appender.list).hasSize(1);
        String renderedThrowable = ThrowableProxyUtil.asString(appender.list.get(0).getThrowableProxy());
        assertThat(renderedThrowable)
                .contains(SystemException.class.getName())
                .contains("[SYS_TEST]")
                .contains("锁后端不可用：redis")
                .contains("at " + ExceptionLoggingTest.class.getName()
                        + ".logsSystemExceptionWithCauseInThrowableRendering(ExceptionLoggingTest.java:")
                .contains(IllegalStateException.class.getName())
                .contains("redis down");
    }
}
