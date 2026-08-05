package com.github.leyland.letool.job.exception;

import com.github.leyland.letool.exception.core.SystemException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobException} 统一异常契约测试。
 */
class JobExceptionTest {

    /**
     * 验证任务异常继承统一系统异常并保留任务名和底层原因。
     */
    @Test
    void shouldExposeStableErrorCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("quartz failed");

        JobException exception = new JobException(
                JobErrorCode.SCHEDULER_OPERATION_FAILED, "sync", cause, "暂停");

        assertThat(exception).isInstanceOf(SystemException.class);
        assertThat(exception.getCode()).isEqualTo("JOB_006");
        assertThat(exception.getJobName()).isEqualTo("sync");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).contains("暂停");
    }
}
