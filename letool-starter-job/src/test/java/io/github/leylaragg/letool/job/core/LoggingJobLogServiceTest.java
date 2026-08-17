package io.github.leylaragg.letool.job.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoggingJobLogService} 默认结构化日志测试。
 */
class LoggingJobLogServiceTest {

    /**
     * 验证独立 logger 输出稳定字段且不输出业务参数。
     */
    @Test
    void shouldWriteSafeStructuredSummary() {
        Logger logger = (Logger) LoggerFactory.getLogger("letool.job");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Instant now = Instant.parse("2026-08-05T08:00:00Z");
            JobContext context = new JobContext(
                    "execution-1", "sync", 0, 1, 0, JobTriggerType.CRON,
                    now, now, "fire-1", "node-a", Map.of("secret", "hidden"));

            new LoggingJobLogService().record(JobExecutionRecord.success(context, now.plusMillis(20)));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("jobName=sync", "status=SUCCESS", "durationMs=20");
                assertThat(event.getFormattedMessage()).doesNotContain("secret", "hidden");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
