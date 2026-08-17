package io.github.leylaragg.letool.job.core;

/**
 * 一次任务执行尝试的最终状态。
 */
public enum JobStatus {

    /** 当前尝试执行成功。 */
    SUCCESS,

    /** 当前尝试失败，但已经成功安排下一次重试。 */
    RETRY_SCHEDULED,

    /** 当前尝试失败且不会继续重试。 */
    FAILED
}
