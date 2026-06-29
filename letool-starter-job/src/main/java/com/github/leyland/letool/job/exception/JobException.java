package com.github.leyland.letool.job.exception;

/**
 * 任务调度异常——任务注册、执行、调度过程中出现异常时抛出.
 *
 * <p>此异常为 {@link RuntimeException} 子类，携带任务名称信息，
 * 便于上层捕获后根据任务名进行针对性的错误处理或告警.</p>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>任务注册失败（如任务名重复）</li>
 *   <li>任务调度异常（如 Cron 表达式解析失败）</li>
 *   <li>任务执行过程中抛出未捕获异常</li>
 *   <li>任务暂停/恢复操作失败</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * throw new JobException("任务注册失败：任务名重复", "dailyReportJob");
 *
 * throw new JobException("Cron 表达式解析失败", "syncJob",
 *         new IllegalArgumentException("Invalid cron: 0 0 *"));
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JobException extends RuntimeException {

    // ======================== 成员变量 ========================

    /**
     * 关联的任务名称.
     */
    private final String jobName;

    // ======================== 构造方法 ========================

    /**
     * 创建任务异常.
     *
     * @param message 错误描述信息
     * @param jobName 关联的任务名称
     */
    public JobException(String message, String jobName) {
        super(message);
        this.jobName = jobName;
    }

    /**
     * 创建包裹原始异常的任务异常.
     *
     * @param message 错误描述信息
     * @param jobName 关联的任务名称
     * @param cause   原始异常（如 Cron 解析异常）
     */
    public JobException(String message, String jobName, Throwable cause) {
        super(message, cause);
        this.jobName = jobName;
    }

    // ======================== 公共方法 ========================

    /**
     * 获取关联的任务名称.
     *
     * @return 任务名称
     */
    public String getJobName() {
        return jobName;
    }
}
