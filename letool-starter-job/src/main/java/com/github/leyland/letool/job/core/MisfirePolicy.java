package com.github.leyland.letool.job.core;

/**
 * Cron 触发错过计划时间后的处理策略。
 */
public enum MisfirePolicy {

    /** 跳过已经错过的触发，等待下一次正常计划。 */
    DO_NOTHING,

    /** 立即补执行一次，然后恢复正常计划。 */
    FIRE_ONCE_NOW
}
