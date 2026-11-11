package io.github.leylaragg.letool.print.autoconfigure;

import com.openhtmltopdf.util.XRLog;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * 在打印自动装配时应用 OpenHTMLtoPDF 日志等级。
 *
 * <p>首次设置等级可能触发上游日志初始化。应用等级后恢复宿主已有的 JUL 输出策略。</p>
 *
 * @author leyland
 */
final class PrintPdfLoggingInitializer implements InitializingBean {

    /** Spring 绑定的打印配置。 */
    private final PrintProperties properties;

    /**
     * 创建 PDF 日志初始化器。
     *
     * @param properties 已绑定的打印配置
     */
    PrintPdfLoggingInitializer(PrintProperties properties) {
        this.properties = properties;
    }

    /**
     * 将配置的等级应用到 OpenHTMLtoPDF 已注册的日志记录器。
     *
     * @throws IllegalArgumentException 配置的日志等级不受支持时抛出
     */
    @Override
    public void afterPropertiesSet() {
        Optional<Level> level = properties.pdfLogLevel();
        // 未显式配置时不访问 XRLog，避免触发上游日志初始化。
        if (level.isEmpty()) {
            return;
        }

        List<String> loggerNames = XRLog.listRegisteredLoggers();
        List<JulLoggerOutput> hostOutputs = configuredJulOutputs(loggerNames);
        try {
            for (String name : loggerNames) {
                XRLog.setLevel(name, level.get());
            }
        } finally {
            // JDKXRLogger 首次初始化可能重装处理器和父级转发。恢复输出策略，保留本次设置的等级。
            for (JulLoggerOutput output : hostOutputs) {
                output.restore();
            }
        }
    }

    /**
     * 保存宿主已有的 JUL 输出设置，供日志初始化结束后恢复。
     *
     * @param loggerNames OpenHTMLtoPDF 已注册的日志记录器名称
     * @return 已有宿主设置的日志输出快照
     */
    private List<JulLoggerOutput> configuredJulOutputs(List<String> loggerNames) {
        LogManager logManager = LogManager.getLogManager();
        List<JulLoggerOutput> outputs = new ArrayList<>();
        for (String name : loggerNames) {
            Logger logger = logManager.getLogger(name);
            // 缺失或仍为默认状态的记录器交给上游初始化。
            if (logger == null) {
                continue;
            }
            Handler[] handlers = logger.getHandlers();
            if (handlers.length == 0 && logger.getLevel() == null
                    && logger.getFilter() == null && logger.getUseParentHandlers()) {
                continue;
            }

            List<JulHandlerOutput> handlerOutputs = new ArrayList<>(handlers.length);
            for (Handler handler : handlers) {
                handlerOutputs.add(new JulHandlerOutput(handler, handler.getFormatter()));
            }
            outputs.add(new JulLoggerOutput(logger, logger.getUseParentHandlers(), handlerOutputs));
        }
        return outputs;
    }

    /**
     * 单个 JUL 日志记录器在上游初始化前的输出设置。
     *
     * @param logger 宿主已配置的日志记录器
     * @param useParentHandlers 是否交给父级处理器输出
     * @param handlers 按原挂载顺序保存的处理器
     */
    private record JulLoggerOutput(Logger logger, boolean useParentHandlers, List<JulHandlerOutput> handlers) {

        /** 恢复处理器及其挂载顺序和父级转发设置。 */
        void restore() {
            // 处理器及输出流由宿主管理，这里只恢复挂载关系。
            for (Handler current : logger.getHandlers()) {
                logger.removeHandler(current);
            }
            for (JulHandlerOutput output : handlers) {
                output.restoreFormatter();
                logger.addHandler(output.handler());
            }
            logger.setUseParentHandlers(useParentHandlers);
        }
    }

    /**
     * 日志处理器及其原有输出格式。
     *
     * @param handler 宿主持有的日志处理器
     * @param formatter 原有格式器，未配置时为 {@code null}
     */
    private record JulHandlerOutput(Handler handler, Formatter formatter) {

        /** 上游改动了宿主已设置的输出格式时，恢复原格式器。 */
        void restoreFormatter() {
            // 宿主未设置格式器时，沿用上游初始化选择的格式。
            if (formatter != null && handler.getFormatter() != formatter) {
                handler.setFormatter(formatter);
            }
        }
    }
}
