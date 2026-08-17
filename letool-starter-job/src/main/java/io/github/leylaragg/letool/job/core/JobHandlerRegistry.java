package io.github.leylaragg.letool.job.core;

/**
 * 管理当前调度节点可调用的任务处理器。
 */
public interface JobHandlerRegistry {

    /**
     * 注册任务处理器。
     *
     * @param jobName 逻辑任务名称
     * @param handler 业务处理器
     */
    void register(String jobName, JobHandler handler);

    /**
     * 获取必需的任务处理器。
     *
     * @param jobName 逻辑任务名称
     * @return 已注册处理器
     */
    JobHandler getRequired(String jobName);

    /**
     * 判断处理器是否存在。
     *
     * @param jobName 逻辑任务名称
     * @return 存在时返回 {@code true}
     */
    boolean contains(String jobName);

    /**
     * 注销当前节点处理器。
     *
     * @param jobName 逻辑任务名称
     */
    void unregister(String jobName);
}
