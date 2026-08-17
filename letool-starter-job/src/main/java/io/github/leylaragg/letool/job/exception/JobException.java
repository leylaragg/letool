package io.github.leylaragg.letool.job.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * Job 模块统一基础设施异常。
 */
public class JobException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联逻辑任务名称。 */
    private final String jobName;

    /**
     * 创建不含底层原因的任务异常。
     *
     * @param errorCode 稳定错误码
     * @param jobName 关联任务名称；启动期无法确定时可为 {@code null}
     * @param messageArgs 默认消息模板参数
     */
    public JobException(JobErrorCode errorCode, String jobName, Object... messageArgs) {
        super(errorCode, messageArgs, null, null);
        this.jobName = jobName;
    }

    /**
     * 创建保留底层原因的任务异常。
     *
     * @param errorCode 稳定错误码
     * @param jobName 关联任务名称；启动期无法确定时可为 {@code null}
     * @param cause 底层异常
     * @param messageArgs 默认消息模板参数
     */
    public JobException(JobErrorCode errorCode, String jobName, Throwable cause, Object... messageArgs) {
        super(errorCode, messageArgs, null, cause);
        this.jobName = jobName;
    }

    /** @return 关联任务名称；没有明确任务时返回 {@code null} */
    public String getJobName() { return jobName; }
}
