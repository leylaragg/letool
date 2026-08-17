package io.github.leylaragg.letool.job.core;

/**
 * 接收任务执行记录的用户扩展接口。
 *
 * <p>实现可以把记录持久化到数据库、消息队列、搜索引擎或监控系统。
 * 实现失败不会改变已经完成的业务任务结果。</p>
 */
@FunctionalInterface
public interface JobLogService {

    /**
     * 记录一次实际任务执行尝试。
     *
     * @param record 不可变执行记录
     */
    void record(JobExecutionRecord record);
}
