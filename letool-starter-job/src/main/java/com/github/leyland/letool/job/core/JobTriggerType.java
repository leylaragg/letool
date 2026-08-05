package com.github.leyland.letool.job.core;

/**
 * 任务实际执行的触发来源。
 */
public enum JobTriggerType {

    /** Cron 计划触发。 */
    CRON,

    /** 用户手动触发。 */
    MANUAL,

    /** 失败后的延迟重试触发。 */
    RETRY,

    /** Quartz 集群节点故障恢复触发。 */
    RECOVERY
}
