package com.github.leyland.letool.monitor.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 数据清理任务模型.
 *
 * <p>封装单个数据表的清理策略，包括表名、保留天数、上次清理时间等信息。
 * 任务通过 {@link #shouldCleanup()} 判断是否需要执行，通过 {@link #execute()} 执行实际的清理操作。</p>
 *
 * <p>当前版本为轻量级实现（日志输出），后续可扩展为实际数据库操作。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class CleanupTask {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);

    // ======================== 字段 ========================

    /** 数据表名称 */
    private final String tableName;

    /** 数据保留天数 */
    private final int retentionDays;

    /** 上次清理时间戳（毫秒） */
    private long lastCleanupTime;

    // ======================== 构造方法 ========================

    /**
     * 创建清理任务.
     *
     * @param tableName     数据表名称
     * @param retentionDays 数据保留天数
     */
    public CleanupTask(String tableName, int retentionDays) {
        this.tableName = tableName;
        this.retentionDays = retentionDays;
        this.lastCleanupTime = 0;
    }

    // ======================== 公共方法 ========================

    /**
     * 判断是否需要进行清理.
     *
     * <p>规则：如果距离上次清理已超过保留天数，则需要清理。</p>
     *
     * @return {@code true} 如果需要进行清理
     */
    public boolean shouldCleanup() {
        if (lastCleanupTime == 0) return true;
        long elapsedDays = (System.currentTimeMillis() - lastCleanupTime) / (24 * 60 * 60 * 1000L);
        return elapsedDays >= retentionDays;
    }

    /**
     * 执行清理操作.
     *
     * <p>当前版本通过日志输出将被清理的数据描述，实际操作待后续扩展。</p>
     *
     * @return 被清理的记录数（当前版本始终返回 0）
     */
    public long execute() {
        log.info("[Monitor-Cleanup] 开始清理数据表: {}, 保留天数: {}, 清理早于 {} 的数据",
                tableName, retentionDays,
                LocalDateTime.now().minusDays(retentionDays));

        // 更新上次清理时间
        lastCleanupTime = System.currentTimeMillis();

        log.info("[Monitor-Cleanup] 数据表 {} 清理完成（当前为占位实现，实际清理逻辑待扩展）", tableName);
        return 0;
    }

    // ======================== Getter ========================

    /** @return 数据表名称 */
    public String getTableName() { return tableName; }

    /** @return 数据保留天数 */
    public int getRetentionDays() { return retentionDays; }

    /** @return 上次清理时间戳（毫秒） */
    public long getLastCleanupTime() { return lastCleanupTime; }

    /** @return 上次清理时间的可读格式 */
    public LocalDateTime getLastCleanupDateTime() {
        if (lastCleanupTime == 0) return null;
        return LocalDateTime.ofEpochSecond(lastCleanupTime / 1000, 0, java.time.ZoneOffset.ofHours(8));
    }

    @Override
    public String toString() {
        return "CleanupTask{table=" + tableName + ", retentionDays=" + retentionDays
                + ", lastCleanup=" + (lastCleanupTime == 0 ? "never"
                : LocalDateTime.ofEpochSecond(lastCleanupTime / 1000, 0, java.time.ZoneOffset.ofHours(8))) + "}";
    }
}
