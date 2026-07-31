package com.github.leyland.letool.log.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.leyland.letool.tool.json.Fastjson2JsonCodec;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Slf4jAuditLogService} 默认审计日志输出测试。
 */
class Slf4jAuditLogServiceTest {

    /**
     * 默认实现应向专用 Logger 输出单行结构化 JSON。
     */
    @Test
    void shouldWriteStructuredJsonToDedicatedLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(Slf4jAuditLogService.AUDIT_LOGGER_NAME);
        Level originalLevel = logger.getLevel();
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            Slf4jAuditLogService service =
                    new Slf4jAuditLogService(Fastjson2JsonCodec.createDefault());
            AuditLogEvent event = AuditLogEvent.builder()
                    .traceId("trace-json")
                    .operation("导出报表")
                    .type(AuditType.ADMIN)
                    .result("SUCCESS")
                    .build();

            service.record(event);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("\"traceId\":\"trace-json\"")
                    .contains("\"operation\":\"导出报表\"")
                    .contains("\"type\":\"ADMIN\"");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
            appender.stop();
        }
    }
}
