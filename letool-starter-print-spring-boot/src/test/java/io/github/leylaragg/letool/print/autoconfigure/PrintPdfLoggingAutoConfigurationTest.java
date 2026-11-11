package io.github.leylaragg.letool.print.autoconfigure;

import com.openhtmltopdf.util.JDKXRLogger;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.XMLFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 JUL 验证日志等级、宿主输出策略和上游冷启动装配。
 *
 * @author leyland
 */
@ResourceLock("openhtmltopdf-logging")
class PrintPdfLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrintAutoConfiguration.class));

    private final List<LoggerState> originalStates = new ArrayList<>();
    private List<String> loggerNames;
    private XRLogger originalLogger;
    private boolean originalLoggingEnabled;
    private RecordingHandler handler;
    private HostJdkLogger hostLogger;
    private LoggerState rootState;
    private LoggerState unrelatedState;

    /** 保存宿主的共享日志状态，再安装本用例的 JUL 输出。 */
    @BeforeEach
    void preserveHostLogging() {
        loggerNames = XRLog.listRegisteredLoggers();
        originalLogger = XRLog.getLoggerImpl();
        originalLoggingEnabled = XRLog.isLoggingEnabled();
        for (String name : loggerNames) {
            originalStates.add(LoggerState.capture(Logger.getLogger(name)));
        }
        rootState = LoggerState.capture(Logger.getLogger(""));
        unrelatedState = LoggerState.capture(Logger.getLogger("host.pdf-logging-test"));

        handler = new RecordingHandler();
        hostLogger = new HostJdkLogger(handler);
        XRLog.setLoggerImpl(hostLogger);
        hostLogger.setLevel(XRLog.GENERAL, Level.FINE);
        hostLogger.levelChanges = 0;
    }

    /** 用例结束后还原 XRLog 实现及 JUL 状态，隔离同一 JVM 中的其他测试。 */
    @AfterEach
    void restoreHostLogging() {
        XRLog.setLoggerImpl(originalLogger);
        XRLog.setLoggingEnabled(originalLoggingEnabled);
        for (LoggerState state : originalStates) {
            state.restore();
        }
        rootState.restore();
        unrelatedState.restore();
    }

    @Test
    void shouldKeepExistingLoggingPolicyWhenUnconfigured() {
        Logger.getLogger(XRLog.GENERAL).setLevel(null);
        Logger.getLogger(XRLog.LOAD).setLevel(Level.SEVERE);
        List<LoggerState> hostStates = currentLoggerStates();
        XRLog.setLoggingEnabled(false);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertHostStrategyUnchanged(hostStates);
            assertThat(XRLog.isLoggingEnabled()).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void shouldKeepExistingLoggingPolicyForBlankLevel(String value) {
        List<LoggerState> hostStates = currentLoggerStates();

        contextRunner.withPropertyValues("letool.print.pdf-log-level=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertHostStrategyUnchanged(hostStates);
        });
    }

    @Test
    void shouldSuppressInfoAndKeepWarningsAndErrorsForEveryRegisteredLogger() {
        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(XRLog.getLoggerImpl()).isSameAs(hostLogger);
            assertThat(hostLogger.levelChanges).isEqualTo(loggerNames.size());
            for (String name : loggerNames) {
                assertThat(Logger.getLogger(name).getLevel()).isEqualTo(Level.WARNING);
                hostLogger.log(name, Level.INFO, "filtered info");
                hostLogger.log(name, Level.WARNING, "kept warning");
                hostLogger.log(name, Level.SEVERE, "kept error");
                assertThat(Logger.getLogger(name).getHandlers()).containsExactly(handler);
                assertThat(Logger.getLogger(name).getUseParentHandlers()).isFalse();
            }
            assertThat(handler.records).hasSize(loggerNames.size() * 2);
            assertThat(handler.records).extracting(LogRecord::getLevel)
                    .containsOnly(Level.WARNING, Level.SEVERE);
            rootState.assertUnchanged();
            unrelatedState.assertUnchanged();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "OFF, OFF", "SEVERE, SEVERE", "WARNING, WARNING", "INFO, INFO", "CONFIG, CONFIG",
            "FINE, FINE", "FINER, FINER", "FINEST, FINEST", "ALL, ALL",
            "WARN, WARNING", "ERROR, SEVERE", "DEBUG, FINE", "TRACE, FINEST",
            "warning, WARNING", "'  debug  ', FINE"
    })
    void shouldApplyNamedJulLevelsAndCommonAliases(String value, String expected) {
        contextRunner.withPropertyValues("letool.print.pdf-log-level=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            for (String name : loggerNames) {
                assertThat(Logger.getLogger(name).getLevel()).isEqualTo(Level.parse(expected));
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"900", "-1", "0", "WARNING/INFO", "private-log-config"})
    void shouldRejectInvalidLevelsWithoutExposingConfiguredValue(String value) {
        contextRunner.withPropertyValues("letool.print.pdf-log-level=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("letool.print.pdf-log-level 配置不合法");
            assertThat(hostLogger.levelChanges).isZero();
        });
    }

    @Test
    void shouldApplyLoggingWithHostProvidedPdfRenderer() {
        contextRunner.withUserConfiguration(PrintAutoConfigurationTest.CustomPdfRendererConfiguration.class)
                .withPropertyValues("letool.print.pdf-log-level=WARNING")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("customPdfRenderer");
                    assertThat(context).doesNotHaveBean("pdfRenderer");
                    hostLogger.log(XRLog.RENDER, Level.INFO, "filtered info");
                    hostLogger.log(XRLog.RENDER, Level.WARNING, "kept warning");
                    assertThat(handler.records).extracting(LogRecord::getLevel).containsExactly(Level.WARNING);
                });
    }

    @Test
    void shouldPreserveHostHandlerThresholdWhenDebugIsConfigured() {
        handler.setLevel(Level.INFO);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=DEBUG").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(Logger.getLogger(XRLog.GENERAL).getLevel()).isEqualTo(Level.FINE);
            assertThat(handler.getLevel()).isEqualTo(Level.INFO);
            hostLogger.log(XRLog.GENERAL, Level.FINE, "handler filters debug");
            hostLogger.log(XRLog.GENERAL, Level.INFO, "handler keeps info");
            assertThat(handler.records).extracting(LogRecord::getLevel).containsExactly(Level.INFO);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"WARNING", "private-log-config"})
    void shouldNotApplyLoggingWhenStarterIsDisabled(String value) {
        List<LoggerState> hostStates = currentLoggerStates();

        contextRunner.withPropertyValues("letool.print.enabled=false", "letool.print.pdf-log-level=" + value)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PrintProperties.class);
                    assertHostStrategyUnchanged(hostStates);
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveHostOutputWhenJdkProviderInitializesCold(boolean customProvider) {
        resetNamedLoggerConfiguration();
        RecordingHandler parentHandler = configureParentOutput();
        Formatter hostFormatter = new XMLFormatter();
        handler.setFormatter(hostFormatter);
        Logger host = Logger.getLogger(XRLog.GENERAL);
        host.setLevel(Level.INFO);
        host.addHandler(handler);
        host.setUseParentHandlers(true);
        RecordingHandler providerHandler = new RecordingHandler();
        XRLogger provider = customProvider
                ? new JDKXRLogger(false, Level.CONFIG, providerHandler, new SimpleFormatter())
                : new JDKXRLogger();
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(XRLog.getLoggerImpl()).isSameAs(provider);
            assertThat(host.getHandlers()).containsExactly(handler);
            assertThat(host.getUseParentHandlers()).isTrue();
            assertThat(host.getLevel()).isEqualTo(Level.WARNING);
            assertThat(handler.getFormatter()).isSameAs(hostFormatter);
            logInfoWarningAndError(provider);
            assertWarningAndErrorRecords(handler);
            assertWarningAndErrorRecords(parentHandler);

            Logger unconfigured = Logger.getLogger(XRLog.LOAD);
            assertThat(unconfigured.getUseParentHandlers()).isFalse();
            if (customProvider) {
                assertThat(unconfigured.getHandlers()).containsExactly(providerHandler);
                assertThat(providerHandler.records).isEmpty();
            } else {
                assertThat(unconfigured.getHandlers()).singleElement().isInstanceOf(ConsoleHandler.class);
            }
        });
    }

    @Test
    void shouldPreserveHostFormatterWhenColdProviderReusesHostHandler() {
        resetNamedLoggerConfiguration();
        RecordingHandler parentHandler = configureParentOutput();
        Formatter hostFormatter = new XMLFormatter();
        handler.setFormatter(hostFormatter);
        Logger host = Logger.getLogger(XRLog.GENERAL);
        host.addHandler(handler);
        host.setUseParentHandlers(true);
        XRLogger provider = new JDKXRLogger(true, Level.CONFIG, handler, new SimpleFormatter());
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(host.getHandlers()).containsExactly(handler);
            assertThat(host.getUseParentHandlers()).isTrue();
            assertThat(handler.getFormatter()).isSameAs(hostFormatter);
            logInfoWarningAndError(provider);
            assertWarningAndErrorRecords(handler);
            assertWarningAndErrorRecords(parentHandler);
        });
    }

    @Test
    void shouldKeepDefaultJdkOutputForUnconfiguredExistingLoggers() {
        resetNamedLoggerConfiguration();
        XRLogger provider = new JDKXRLogger();
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(XRLog.getLoggerImpl()).isSameAs(provider);
            for (String name : loggerNames) {
                Logger logger = Logger.getLogger(name);
                assertThat(logger.getLevel()).isEqualTo(Level.WARNING);
                assertThat(logger.getUseParentHandlers()).isFalse();
                assertThat(logger.getHandlers()).singleElement().isInstanceOf(ConsoleHandler.class);
                assertThat(logger.getHandlers()[0].getFormatter()).isNotNull();
            }
        });
    }

    @Test
    void shouldKeepColdProviderOutputForUnconfiguredExistingLoggers() {
        resetNamedLoggerConfiguration();
        RecordingHandler providerHandler = new RecordingHandler();
        Formatter providerFormatter = new XMLFormatter();
        XRLogger provider = new JDKXRLogger(false, Level.CONFIG, providerHandler, providerFormatter);
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            for (String name : loggerNames) {
                Logger logger = Logger.getLogger(name);
                assertThat(logger.getLevel()).isEqualTo(Level.WARNING);
                assertThat(logger.getHandlers()).containsExactly(providerHandler);
                assertThat(logger.getUseParentHandlers()).isFalse();
            }
            assertThat(providerHandler.getFormatter()).isSameAs(providerFormatter);
            logInfoWarningAndError(provider);
            assertWarningAndErrorRecords(providerHandler);
        });
    }

    @Test
    void shouldPreserveHostParentOutputWithoutDirectHandlersOnColdInitialization() {
        resetNamedLoggerConfiguration();
        RecordingHandler parentHandler = configureParentOutput();
        Logger host = Logger.getLogger(XRLog.GENERAL);
        host.setLevel(Level.INFO);
        RecordingHandler providerHandler = new RecordingHandler();
        XRLogger provider = new JDKXRLogger(false, Level.CONFIG, providerHandler, new SimpleFormatter());
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(host.getHandlers()).isEmpty();
            assertThat(host.getUseParentHandlers()).isTrue();
            assertThat(host.getLevel()).isEqualTo(Level.WARNING);
            logInfoWarningAndError(provider);
            assertWarningAndErrorRecords(parentHandler);
            assertThat(providerHandler.records).isEmpty();
        });
    }

    @Test
    void shouldPreserveExplicitlyDisabledHostOutputOnColdInitialization() {
        resetNamedLoggerConfiguration();
        Logger host = Logger.getLogger(XRLog.GENERAL);
        host.setUseParentHandlers(false);
        RecordingHandler providerHandler = new RecordingHandler();
        XRLogger provider = new JDKXRLogger(false, Level.CONFIG, providerHandler, new SimpleFormatter());
        XRLog.setLoggerImpl(provider);

        contextRunner.withPropertyValues("letool.print.pdf-log-level=WARNING").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(host.getHandlers()).isEmpty();
            assertThat(host.getUseParentHandlers()).isFalse();
            assertThat(host.getLevel()).isEqualTo(Level.WARNING);
            assertThat(Logger.getLogger(XRLog.LOAD).getHandlers()).containsExactly(providerHandler);
            logInfoWarningAndError(provider);
            assertThat(providerHandler.records).isEmpty();
        });
    }

    @Test
    void shouldNotInitializeColdProviderWhenLevelIsUnconfigured() {
        resetNamedLoggerConfiguration();
        Formatter hostFormatter = new XMLFormatter();
        handler.setFormatter(hostFormatter);
        HostJdkLogger provider = new HostJdkLogger(handler);
        XRLog.setLoggerImpl(provider);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(XRLog.getLoggerImpl()).isSameAs(provider);
            assertThat(provider.levelChanges).isZero();
            assertThat(handler.getFormatter()).isSameAs(hostFormatter);
            for (String name : loggerNames) {
                Logger logger = Logger.getLogger(name);
                assertThat(logger.getLevel()).isNull();
                assertThat(logger.getHandlers()).isEmpty();
                assertThat(logger.getUseParentHandlers()).isTrue();
            }
        });
    }

    /** 清空命名记录器的配置，供冷启动用例验证上游默认初始化。 */
    private void resetNamedLoggerConfiguration() {
        for (String name : loggerNames) {
            Logger logger = Logger.getLogger(name);
            for (Handler current : logger.getHandlers()) {
                logger.removeHandler(current);
            }
            logger.setLevel(null);
            logger.setUseParentHandlers(true);
            logger.setFilter(null);
        }
    }

    /**
     * 安装父级 JUL 输出，检查子记录器是否仍按宿主设置转发。
     *
     * @return 接收父级日志记录的测试处理器
     */
    private RecordingHandler configureParentOutput() {
        Logger parent = Logger.getLogger("com.openhtmltopdf");
        originalStates.add(LoggerState.capture(parent));
        for (Handler current : parent.getHandlers()) {
            parent.removeHandler(current);
        }
        RecordingHandler parentHandler = new RecordingHandler();
        parent.addHandler(parentHandler);
        parent.setUseParentHandlers(false);
        parent.setLevel(Level.ALL);
        parent.setFilter(null);
        return parentHandler;
    }

    private void logInfoWarningAndError(XRLogger provider) {
        provider.log(XRLog.GENERAL, Level.INFO, "filtered info");
        provider.log(XRLog.GENERAL, Level.WARNING, "kept warning");
        provider.log(XRLog.GENERAL, Level.SEVERE, "kept error");
    }

    private void assertWarningAndErrorRecords(RecordingHandler output) {
        assertThat(output.records).extracting(LogRecord::getLevel).containsExactly(Level.WARNING, Level.SEVERE);
    }

    private List<LoggerState> currentLoggerStates() {
        List<LoggerState> states = new ArrayList<>();
        for (String name : loggerNames) {
            states.add(LoggerState.capture(Logger.getLogger(name)));
        }
        return states;
    }

    private void assertHostStrategyUnchanged(List<LoggerState> hostStates) {
        assertThat(XRLog.getLoggerImpl()).isSameAs(hostLogger);
        assertThat(hostLogger.levelChanges).isZero();
        for (LoggerState state : hostStates) {
            state.assertUnchanged();
        }
        rootState.assertUnchanged();
        unrelatedState.assertUnchanged();
    }

    /**
     * 测试前保存的 JUL 状态，用于断言宿主策略和用例结束后的恢复。
     *
     * @param logger 被保存的日志记录器
     * @param level 原有等级，继承父级时为 {@code null}
     * @param handlers 按挂载顺序保存的处理器
     * @param formatters 与处理器顺序对应的原有格式器
     * @param useParentHandlers 是否交给父级处理器输出
     * @param filter 原有的日志过滤器
     */
    private record LoggerState(Logger logger, Level level, Handler[] handlers, Formatter[] formatters,
                               boolean useParentHandlers, Filter filter) {

        static LoggerState capture(Logger logger) {
            Handler[] handlers = logger.getHandlers();
            Formatter[] formatters = new Formatter[handlers.length];
            for (int index = 0; index < handlers.length; index++) {
                formatters[index] = handlers[index].getFormatter();
            }
            return new LoggerState(logger, logger.getLevel(), handlers, formatters, logger.getUseParentHandlers(), logger.getFilter());
        }

        void assertUnchanged() {
            assertThat(logger.getLevel()).isEqualTo(level);
            assertThat(logger.getHandlers()).containsExactly(handlers);
            assertThat(logger.getUseParentHandlers()).isEqualTo(useParentHandlers);
            assertThat(logger.getFilter()).isSameAs(filter);
            for (int index = 0; index < handlers.length; index++) {
                assertThat(handlers[index].getFormatter()).isSameAs(formatters[index]);
            }
        }

        void restore() {
            // handler 归宿主所有，恢复挂载关系即可，不能关闭宿主输出流。
            for (Handler current : logger.getHandlers()) {
                logger.removeHandler(current);
            }
            for (int index = 0; index < handlers.length; index++) {
                Handler original = handlers[index];
                if (formatters[index] != null && original.getFormatter() != formatters[index]) {
                    original.setFormatter(formatters[index]);
                }
                logger.addHandler(original);
            }
            logger.setLevel(level);
            logger.setUseParentHandlers(useParentHandlers);
            logger.setFilter(filter);
        }
    }

    /** 执行真实 JUL 等级设置并记录调用次数，用于验证未配置时的初始化边界。 */
    private static final class HostJdkLogger extends JDKXRLogger {

        private int levelChanges;

        private HostJdkLogger(Handler handler) {
            super(false, Level.FINE, handler, new SimpleFormatter());
        }

        @Override
        public void setLevel(String name, Level level) {
            super.setLevel(name, level);
            levelChanges++;
        }
    }

    /** 收集通过 JUL 过滤的记录，供用例检查实际输出。 */
    private static final class RecordingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        private RecordingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
