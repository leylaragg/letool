package com.github.leyland.letool.cache.consistency;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 基于业务数据源的 JDBC Outbox 实现。
 *
 * <p>本实现不自行开启事务。调用方必须让 {@link #append(CacheInvalidationEvent)} 与业务 SQL
 * 使用同一个 Spring 事务管理器。事件领取采用“查询候选 + 条件更新”的方式，兼容常见关系型数据库，
 * 并保证并发实例中只有条件更新成功的一方获得事件。</p>
 */
public class JdbcCacheInvalidationEventStore implements CacheInvalidationEventStore {

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    /** 业务数据源对应的 JDBC 操作入口。 */
    private final JdbcTemplate jdbcTemplate;
    /** Outbox 表名，只允许安全标识符。 */
    private final String tableName;

    /**
     * 创建 JDBC 事件仓储。
     *
     * @param jdbcTemplate 业务数据源对应的 JDBC 模板
     * @param tableName Outbox 表名
     */
    public JdbcCacheInvalidationEventStore(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate 不能为空");
        this.tableName = Objects.requireNonNull(tableName, "Outbox 表名不能为空");
        if (!SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Outbox 表名只能包含字母、数字和下划线");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void append(CacheInvalidationEvent event) {
        Objects.requireNonNull(event, "缓存失效事件不能为空");
        jdbcTemplate.update("INSERT INTO " + tableName + " ("
                        + "event_id, cache_name, serialized_key, fence_token, status, attempt_count, "
                        + "next_attempt_at, lease_owner, lease_until, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)",
                event.eventId(), event.cacheName(), event.serializedKey(), event.fenceToken(),
                event.status().name(), event.attemptCount(), timestamp(event.nextAttemptAt()),
                timestamp(event.createdAt()), timestamp(event.createdAt()));
    }

    /** {@inheritDoc} */
    @Override
    public List<CacheInvalidationEvent> claimBatch(
            Instant now, int batchSize, Duration lease, String owner) {
        Objects.requireNonNull(now, "当前时间不能为空");
        Objects.requireNonNull(lease, "租约时长不能为空");
        Objects.requireNonNull(owner, "租约持有者不能为空");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("批量领取数量必须大于零");
        }
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("租约时长必须大于零");
        }

        Timestamp current = timestamp(now);
        List<String> candidates = jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(
                    "SELECT event_id FROM " + tableName
                            + " WHERE status IN ('PENDING', 'PROCESSING')"
                            + " AND next_attempt_at <= ?"
                            + " AND (lease_until IS NULL OR lease_until <= ?)"
                            + " ORDER BY created_at, event_id");
            statement.setTimestamp(1, current);
            statement.setTimestamp(2, current);
            statement.setMaxRows(batchSize);
            return statement;
        }, (resultSet, rowNumber) -> resultSet.getString("event_id"));

        List<CacheInvalidationEvent> claimed = new ArrayList<>(candidates.size());
        Timestamp leaseUntil = timestamp(now.plus(lease));
        for (String eventId : candidates) {
            int updated = jdbcTemplate.update(
                    "UPDATE " + tableName
                            + " SET status = 'PROCESSING', lease_owner = ?, lease_until = ?, updated_at = ?"
                            + " WHERE event_id = ?"
                            + " AND status IN ('PENDING', 'PROCESSING')"
                            + " AND next_attempt_at <= ?"
                            + " AND (lease_until IS NULL OR lease_until <= ?)",
                    owner, leaseUntil, current, eventId, current, current);
            if (updated == 1) {
                claimed.add(load(eventId));
            }
        }
        return claimed;
    }

    /** {@inheritDoc} */
    @Override
    public void markCompleted(String eventId) {
        jdbcTemplate.update("UPDATE " + tableName
                        + " SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL, updated_at = ?"
                        + " WHERE event_id = ?",
                timestamp(Instant.now()), eventId);
    }

    /** {@inheritDoc} */
    @Override
    public void markRetry(String eventId, Instant nextAttemptAt) {
        jdbcTemplate.update("UPDATE " + tableName
                        + " SET status = 'PENDING', attempt_count = attempt_count + 1,"
                        + " next_attempt_at = ?, lease_owner = NULL, lease_until = NULL, updated_at = ?"
                        + " WHERE event_id = ?",
                timestamp(nextAttemptAt), timestamp(Instant.now()), eventId);
    }

    private CacheInvalidationEvent load(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT event_id, cache_name, serialized_key, fence_token, status, attempt_count,"
                        + " next_attempt_at, created_at FROM " + tableName + " WHERE event_id = ?",
                (resultSet, rowNumber) -> new CacheInvalidationEvent(
                        resultSet.getString("event_id"), resultSet.getString("cache_name"),
                        resultSet.getString("serialized_key"), resultSet.getString("fence_token"),
                        CacheInvalidationEventStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("attempt_count"),
                        resultSet.getTimestamp("next_attempt_at").toInstant(),
                        resultSet.getTimestamp("created_at").toInstant()),
                eventId);
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
