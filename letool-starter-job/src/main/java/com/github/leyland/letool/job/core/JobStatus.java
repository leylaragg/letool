package com.github.leyland.letool.job.core;

/**
 * 任务执行状态枚举.
 *
 * <p>定义任务在生命周期中的各种状态，用于追踪任务的执行情况和当前阶段.
 * 该枚举被 {@link JobResult} 和 {@link JobScheduler} 共同使用，
 * 以统一表示任务在不同阶段的运行状态.</p>
 *
 * <h3>状态流转</h3>
 * <pre>{@code
 * PENDING → RUNNING → SUCCESS
 *                  → FAIL（含重试逻辑）
 *                  → TIMEOUT
 * RUNNING ↔ PAUSED
 * }</pre>
 *
 * <h3>状态说明</h3>
 * <ul>
 *   <li>{@link #PENDING} — 任务已注册但尚未开始执行</li>
 *   <li>{@link #RUNNING} — 任务正在执行中</li>
 *   <li>{@link #SUCCESS} — 任务执行成功</li>
 *   <li>{@link #FAIL} — 任务执行失败（含重试后仍失败的情况）</li>
 *   <li>{@link #TIMEOUT} — 任务执行超时</li>
 *   <li>{@link #PAUSED} — 任务已被暂停</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum JobStatus {

    // ======================== 枚举值 ========================

    /**
     * 待执行：任务已注册但尚未开始执行.
     */
    PENDING,

    /**
     * 执行中：任务正在运行.
     */
    RUNNING,

    /**
     * 执行成功：任务正常完成.
     */
    SUCCESS,

    /**
     * 执行失败：任务执行过程中抛出异常.
     */
    FAIL,

    /**
     * 执行超时：任务执行时间超过设定阈值.
     */
    TIMEOUT,

    /**
     * 已暂停：任务被手动暂停，暂停期间不会触发调度.
     */
    PAUSED;
}
