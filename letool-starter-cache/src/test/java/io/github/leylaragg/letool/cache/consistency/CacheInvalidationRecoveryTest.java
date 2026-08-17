package io.github.leylaragg.letool.cache.consistency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DURABLE 失效事件恢复测试。
 */
class CacheInvalidationRecoveryTest {

    @Test
    void shouldCompleteClaimedEventIdempotently() {
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        Instant now = Instant.now();
        CacheInvalidationEvent event = CacheInvalidationEvent.pending(
                "event-1", "users", "u1", "token-1", now);
        when(eventStore.claimBatch(any(), any(Integer.class), any(), any())).thenReturn(List.of(event));
        when(fenceStore.complete(any())).thenReturn(CacheFenceCompletion.ALREADY_COMPLETED);
        CacheInvalidationRecovery recovery = new CacheInvalidationRecovery(
                eventStore, fenceStore, "node-1", 10,
                Duration.ofSeconds(30), Duration.ofSeconds(1));

        assertEquals(1, recovery.recoverOnce(now));
        verify(eventStore).markCompleted("event-1", null);
    }

    @Test
    void shouldScheduleRetryWhenRedisIsUnavailable() {
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        Instant now = Instant.now();
        CacheInvalidationEvent event = CacheInvalidationEvent.pending(
                "event-1", "users", "u1", "token-1", now);
        when(eventStore.claimBatch(any(), any(Integer.class), any(), any())).thenReturn(List.of(event));
        when(fenceStore.complete(any())).thenThrow(new CacheFenceUnavailableException());
        CacheInvalidationRecovery recovery = new CacheInvalidationRecovery(
                eventStore, fenceStore, "node-1", 10,
                Duration.ofSeconds(30), Duration.ofSeconds(2));

        assertEquals(0, recovery.recoverOnce(now));
        verify(eventStore).markRetry("event-1", null, now.plusSeconds(2));
    }

    @Test
    void shouldExposeBacklogAndCleanExpiredCompletedEvents() {
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        Instant now = Instant.parse("2026-08-12T08:00:00Z");
        CacheInvalidationBacklog backlog = new CacheInvalidationBacklog(3, 2, 10, now.minusSeconds(60));
        when(eventStore.backlog(now)).thenReturn(backlog);
        when(eventStore.deleteCompletedBefore(now.minus(Duration.ofDays(7)), 500)).thenReturn(500);
        CacheInvalidationRecovery recovery = new CacheInvalidationRecovery(
                eventStore, fenceStore, "node-1", 10,
                Duration.ofSeconds(30), Duration.ofSeconds(1));

        assertEquals(backlog, recovery.backlog(now));
        assertEquals(500, recovery.cleanupCompleted(now, Duration.ofDays(7), 500));
        verify(eventStore).deleteCompletedBefore(now.minus(Duration.ofDays(7)), 500);
    }
}
