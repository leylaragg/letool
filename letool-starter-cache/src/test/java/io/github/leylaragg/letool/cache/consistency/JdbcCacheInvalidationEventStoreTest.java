package io.github.leylaragg.letool.cache.consistency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBC 缓存失效事件仓储集成测试。
 */
@DisplayName("JDBC 缓存失效 Outbox")
class JdbcCacheInvalidationEventStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcCacheInvalidationEventStore eventStore;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE letool_cache_outbox (
                  event_id VARCHAR(64) PRIMARY KEY,
                  cache_name VARCHAR(200) NOT NULL,
                  serialized_key VARCHAR(1000) NOT NULL,
                  fence_token VARCHAR(64) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  attempt_count INT NOT NULL,
                  next_attempt_at TIMESTAMP NOT NULL,
                  lease_owner VARCHAR(100),
                  lease_until TIMESTAMP,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """);
        eventStore = new JdbcCacheInvalidationEventStore(jdbcTemplate, "letool_cache_outbox");
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    @DisplayName("业务事务回滚时 Outbox 事件必须同时回滚")
    void appendShouldParticipateInBusinessTransaction() {
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            eventStore.append(event("event-1"));
            throw new IllegalStateException("rollback business");
        }));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM letool_cache_outbox", Integer.class));
    }

    @Test
    @DisplayName("条件领取保证同一个事件只有一个实例占有")
    void claimBatchShouldLeaseEventOnlyOnce() {
        eventStore.append(event("event-1"));
        Instant now = Instant.now().plusSeconds(1);

        List<CacheInvalidationEvent> first = eventStore.claimBatch(
                now, 10, Duration.ofSeconds(30), "node-1");
        List<CacheInvalidationEvent> second = eventStore.claimBatch(
                now, 10, Duration.ofSeconds(30), "node-2");

        assertEquals(List.of("event-1"), first.stream().map(CacheInvalidationEvent::eventId).toList());
        assertEquals(List.of(), second);
        assertTrue(first.get(0).leaseOwner().startsWith("node-1:"));
    }

    @Test
    @DisplayName("过期消费者不得覆盖新租约持有者完成的事件")
    void staleOwnerShouldNotRetryCompletedEvent() {
        eventStore.append(event("event-1"));
        Instant firstClaim = Instant.now().plusSeconds(1);
        eventStore.claimBatch(firstClaim, 1, Duration.ofSeconds(1), "node-1");
        CacheInvalidationEvent reclaimed = eventStore.claimBatch(
                firstClaim.plusSeconds(2), 1, Duration.ofSeconds(30), "node-2").get(0);

        assertTrue(eventStore.markCompleted(reclaimed.eventId(), reclaimed.leaseOwner()));
        assertFalse(eventStore.markRetry("event-1", "node-1", firstClaim.plusSeconds(10)));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM letool_cache_outbox WHERE event_id = 'event-1'", String.class));
    }

    @Test
    @DisplayName("同一恢复实例重新领取时也必须生成新的租约令牌")
    void sameOwnerReclaimShouldRejectExpiredLeaseToken() {
        eventStore.append(event("event-1"));
        Instant firstClaim = Instant.now().plusSeconds(1);
        CacheInvalidationEvent first = eventStore.claimBatch(
                firstClaim, 1, Duration.ofSeconds(1), "node-1").get(0);
        CacheInvalidationEvent reclaimed = eventStore.claimBatch(
                firstClaim.plusSeconds(2), 1, Duration.ofSeconds(30), "node-1").get(0);

        assertNotEquals(first.leaseOwner(), reclaimed.leaseOwner());
        assertFalse(eventStore.markRetry("event-1", first.leaseOwner(), firstClaim.plusSeconds(10)));
        assertTrue(eventStore.markCompleted("event-1", reclaimed.leaseOwner()));
    }

    @Test
    @DisplayName("直接提交后的完成只能更新尚未被恢复任务领取的事件")
    void unleasedCompletionShouldOnlyUpdatePendingEvent() {
        eventStore.append(event("event-1"));

        assertTrue(eventStore.markCompleted("event-1", null));
        assertFalse(eventStore.markCompleted("event-1", null));
    }

    @Test
    @DisplayName("已完成事件按保留时间分批删除并可查询积压快照")
    void shouldCleanupCompletedEventsAndReportBacklog() {
        eventStore.append(event("event-1"));
        eventStore.append(event("event-2"));
        assertTrue(eventStore.markCompleted("event-1", null));

        CacheInvalidationBacklog backlog = eventStore.backlog(Instant.now().plusSeconds(1));

        assertEquals(1, backlog.pendingCount());
        assertEquals(0, backlog.processingCount());
        assertEquals(1, backlog.completedCount());
        assertEquals(1, eventStore.deleteCompletedBefore(Instant.now().plusSeconds(1), 10));
    }

    private CacheInvalidationEvent event(String eventId) {
        return CacheInvalidationEvent.pending(
                eventId, "users", "u1", "token-1", Instant.now());
    }
}
