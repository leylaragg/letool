package com.github.leyland.letool.job.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行日志服务——记录并查询任务执行历史.
 *
 * <p>管理所有任务执行记录，提供内存级别的日志存储（基于 {@link ConcurrentHashMap}）.
 * 每个任务的日志采用环形缓冲区（FIFO链表）存储，超出上限时自动淘汰最早记录.
 * 支持按保留天数清理过期日志.</p>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>内存存储：适合中小规模部署，生产环境建议替换为数据库持久化</li>
 *   <li>环形缓冲：每个任务最多保留 {@link #MAX_LOG_SIZE_PER_JOB} 条记录</li>
 *   <li>线程安全：使用 {@link ConcurrentHashMap} 和同步块保证并发安全</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobResult
 */
public class JobLogService {

    // ======================== 常量 ========================

    private static final Logger log = LoggerFactory.getLogger(JobLogService.class);

    /**
     * 每个任务最多保留的日志条数.
     */
    private static final int MAX_LOG_SIZE_PER_JOB = 200;

    // ======================== 成员变量 ========================

    /**
     * 任务日志存储: 任务名 → 执行记录列表（FIFO链表，新记录在末尾）.
     */
    private final ConcurrentHashMap<String, LinkedList<JobResult>> logs = new ConcurrentHashMap<>();

    // ======================== 记录方法 ========================

    /**
     * 记录一次任务执行结果.
     *
     * <p>如果该任务日志列表已满，自动移除最早记录（FIFO淘汰）.
     * 该方法为线程安全.</p>
     *
     * @param result 任务执行结果
     */
    public void record(JobResult result) {
        LinkedList<JobResult> jobLogs = logs.computeIfAbsent(result.getJobName(), k -> new LinkedList<>());
        synchronized (jobLogs) {
            jobLogs.addLast(result);
            // 环形缓冲：超出上限则淘汰最早记录
            while (jobLogs.size() > MAX_LOG_SIZE_PER_JOB) {
                jobLogs.removeFirst();
            }
        }
        log.debug("记录任务执行日志: jobName={}, executionId={}, status={}",
                result.getJobName(), result.getExecutionId(), result.getStatus());
    }

    // ======================== 查询方法 ========================

    /**
     * 获取指定任务最近的执行日志.
     *
     * <p>返回的列表按时间升序排列（最早的在前）. 如果指定 limit 小于实际条数则返回最后 limit 条.
     * 如果 limit &lt;= 0 则返回全部.</p>
     *
     * @param jobName 任务名称
     * @param limit   最大返回条数，&lt;=0 表示不限制
     * @return 执行结果列表（只读），任务不存在返回空列表
     */
    public List<JobResult> getLogs(String jobName, int limit) {
        LinkedList<JobResult> jobLogs = logs.get(jobName);
        if (jobLogs == null) {
            return Collections.emptyList();
        }
        synchronized (jobLogs) {
            if (limit <= 0 || limit >= jobLogs.size()) {
                return new ArrayList<>(jobLogs);
            }
            // 返回最近 limit 条
            List<JobResult> result = new ArrayList<>(limit);
            int start = jobLogs.size() - limit;
            for (int i = start; i < jobLogs.size(); i++) {
                result.add(jobLogs.get(i));
            }
            return result;
        }
    }

    /**
     * 获取指定任务的最近一次执行结果.
     *
     * @param jobName 任务名称
     * @return 最近一次执行结果，没有记录返回 {@code null}
     */
    public JobResult getLastExecution(String jobName) {
        LinkedList<JobResult> jobLogs = logs.get(jobName);
        if (jobLogs == null || jobLogs.isEmpty()) {
            return null;
        }
        synchronized (jobLogs) {
            return jobLogs.getLast();
        }
    }

    /**
     * 获取指定任务的执行日志总数.
     *
     * @param jobName 任务名称
     * @return 日志条数，任务不存在返回0
     */
    public int getLogCount(String jobName) {
        LinkedList<JobResult> jobLogs = logs.get(jobName);
        if (jobLogs == null) {
            return 0;
        }
        synchronized (jobLogs) {
            return jobLogs.size();
        }
    }

    // ======================== 清理方法 ========================

    /**
     * 清理超过指定天数的过期日志.
     *
     * <p>遍历所有任务日志，移除 {@code startTime} 早于截止日期的记录.
     * 如果某个任务的全部记录都被清理，则移除该任务的条目.</p>
     *
     * @param retentionDays 日志保留天数（基于 startTime 判断）
     * @return 清理的任务记录总数
     */
    public int cleanup(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int totalCleaned = 0;

        for (String jobName : logs.keySet()) {
            LinkedList<JobResult> jobLogs = logs.get(jobName);
            if (jobLogs == null) {
                continue;
            }
            synchronized (jobLogs) {
                int before = jobLogs.size();
                jobLogs.removeIf(r -> r.getStartTime() != null && r.getStartTime().isBefore(cutoff));
                totalCleaned += (before - jobLogs.size());
                if (jobLogs.isEmpty()) {
                    logs.remove(jobName);
                }
            }
        }
        log.info("清理任务日志完成：移除 {} 条记录", totalCleaned);
        return totalCleaned;
    }

    /**
     * 获取当前记录的所有任务名称.
     *
     * @return 任务名称列表
     */
    public List<String> getLoggedJobNames() {
        return new ArrayList<>(logs.keySet());
    }
}
