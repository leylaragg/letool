package com.github.leyland.letool.cache.consistency;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    }

    private CacheInvalidationEvent event(String eventId) {
        return CacheInvalidationEvent.pending(
                eventId, "users", "u1", "token-1", Instant.now());
    }
}
