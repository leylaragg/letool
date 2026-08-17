package io.github.leylaragg.letool.job.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * Job 模块稳定错误码。
 */
public enum JobErrorCode implements ErrorCode {

    /** 任务定义不合法。 */
    INVALID_DEFINITION("JOB_001", "任务定义不合法：{0}"),
    /** 任务名称或持久化定义冲突。 */
    DEFINITION_CONFLICT("JOB_002", "任务定义冲突：{0}"),
    /** 任务不存在。 */
    JOB_NOT_FOUND("JOB_003", "任务不存在：{0}"),
    /** 任务处理方法不合法。 */
    INVALID_HANDLER("JOB_004", "任务处理方法不合法：{0}"),
    /** 当前节点缺少处理器。 */
    HANDLER_NOT_FOUND("JOB_005", "当前节点缺少任务处理器：{0}"),
    /** Quartz 调度操作失败。 */
    SCHEDULER_OPERATION_FAILED("JOB_006", "Quartz 调度操作失败：{0}"),
    /** JDBC 或集群模式禁止本地 Lambda。 */
    CLUSTER_UNSAFE_HANDLER("JOB_007", "当前 JobStore 不允许本地 Lambda：{0}"),
    /** 任务业务执行失败。 */
    EXECUTION_FAILED("JOB_008", "任务执行失败：{0}"),
    /** 重试触发安排失败。 */
    RETRY_SCHEDULING_FAILED("JOB_009", "任务重试安排失败：{0}");

    private final String code;
    private final String defaultMessage;

    JobErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** @return 稳定错误码 */
    @Override
    public String getCode() { return code; }

    /** @return 默认中文错误消息模板 */
    @Override
    public String getDefaultMessage() { return defaultMessage; }
}
