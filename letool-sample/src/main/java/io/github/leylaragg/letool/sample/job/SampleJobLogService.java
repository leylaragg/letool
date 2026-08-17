package io.github.leylaragg.letool.sample.job;

import io.github.leylaragg.letool.job.core.JobExecutionRecord;
import io.github.leylaragg.letool.job.core.JobLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 演示用户自定义任务执行日志扩展。
 *
 * <p>实际项目可以在此使用 MyBatis-Plus、JdbcTemplate、消息队列或监控系统持久化记录。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SampleJobLogService implements JobLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleJobLogService.class);

    /**
     * 接收一次不可变执行记录。
     *
     * @param record 不可变任务执行记录
     */
    @Override
    public void record(JobExecutionRecord record) {
        LOGGER.debug(
                "收到自定义任务记录: jobName={}, executionId={}, status={}, durationMs={}",
                record.getJobName(),
                record.getExecutionId(),
                record.getStatus(),
                record.getDurationMs());
    }
}
