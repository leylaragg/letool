package io.github.leylaragg.letool.cache.consistency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * Spring 事务缓存修改协调器测试。
 */
@DisplayName("Spring 缓存修改事务协调器")
class SpringCacheMutationCoordinatorTest {

    @Test
    @DisplayName("数据库事务提交后才执行缓存动作")
    void shouldApplyCacheActionOnlyAfterCommit() throws Throwable {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(events);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(transactionManager);

        String result = coordinator.execute(
                CacheConsistencyMode.TRANSACTIONAL,
                () -> {
                    events.add("business");
                    return "updated";
                },
                value -> events.add("cache:" + value)
        );

        assertEquals("updated", result);
        assertEquals(List.of("business", "commit", "cache:updated"), events);
    }

    @Test
    @DisplayName("数据库事务回滚时不执行缓存动作")
    void shouldNotApplyCacheActionAfterRollback() {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(events);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(transactionManager);
        IllegalStateException original = new IllegalStateException("database failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                coordinator.execute(
                        CacheConsistencyMode.TRANSACTIONAL,
                        () -> {
                            events.add("business");
                            throw original;
                        },
                        value -> events.add("cache")
                ));

        assertEquals(original, thrown);
        assertEquals(List.of("business", "rollback"), events);
    }

    @Test
    @DisplayName("未装配持久化协议时 DURABLE 必须在业务执行前失败")
    void durableShouldFailBeforeBusinessActionWithoutDurableProtocol() {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(events);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(transactionManager);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                coordinator.execute(
                        CacheConsistencyMode.DURABLE,
                        () -> {
                            events.add("business");
                            return "updated";
                        },
                        value -> events.add("cache")
                ));

        assertEquals("DURABLE 一致性协议尚未装配", thrown.getMessage());
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("DURABLE 在业务前建围栏并在同一事务写 Outbox")
    void durableShouldFenceAndPersistBeforeCompletion() throws Throwable {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(events);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        CacheFence fence = new CacheFence(
                "users", "u1", "event", "token", java.time.Instant.now());
        when(fenceStore.acquire(any(), any(), any())).thenAnswer(invocation -> {
            events.add("fence");
            return new CacheFence("users", "u1", invocation.getArgument(2), "token", fence.createdAt());
        });
        when(fenceStore.complete(any())).thenAnswer(invocation -> {
            events.add("complete");
            return CacheFenceCompletion.COMPLETED;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            events.add("outbox");
            return null;
        }).when(eventStore).append(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            events.add("done");
            return null;
        }).when(eventStore).markCompleted(any(), org.mockito.ArgumentMatchers.isNull());
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(
                transactionManager, fenceStore, eventStore);

        String result = coordinator.execute(
                new CacheMutation(CacheConsistencyMode.DURABLE, "users", "u1"),
                () -> {
                    events.add("business");
                    return "updated";
                },
                value -> events.add("cache:" + value));

        assertEquals("updated", result);
        assertEquals(List.of(
                "fence", "business", "outbox", "commit", "complete", "cache:updated", "done"), events);
        verify(eventStore).append(any());
    }

    @Test
    @DisplayName("同一事务重复修改同一 Key 时复用围栏和 Outbox 事件")
    void durableShouldReuseFenceWithinSameTransaction() throws Throwable {
        List<String> events = new ArrayList<>();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(events);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        when(fenceStore.acquire(any(), any(), any())).thenAnswer(invocation ->
                new CacheFence("users", "u1", invocation.getArgument(2), "token", java.time.Instant.now()));
        when(fenceStore.complete(any())).thenReturn(CacheFenceCompletion.COMPLETED);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(
                transactionManager, fenceStore, eventStore);
        CacheMutation mutation = new CacheMutation(CacheConsistencyMode.DURABLE, "users", "u1");

        String result = coordinator.execute(mutation, () -> {
            events.add("outer-business");
            return coordinator.execute(mutation, () -> {
                events.add("inner-business");
                return "updated";
            }, value -> events.add("inner-cache"));
        }, value -> events.add("outer-cache"));

        assertEquals("updated", result);
        verify(fenceStore, times(1)).acquire(any(), any(), any());
        verify(fenceStore, times(1)).complete(any());
        verify(eventStore, times(1)).append(any());
        assertEquals(List.of(
                "outer-business", "inner-business", "commit", "inner-cache", "outer-cache"), events);
    }

    @Test
    @DisplayName("REQUIRES_NEW 修改同一 Key 时不得复用外层事务围栏")
    void durableShouldNotReuseFenceAcrossRequiresNewTransaction() throws Throwable {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        when(fenceStore.acquire(any(), any(), any())).thenAnswer(invocation ->
                new CacheFence("users", "u1", invocation.getArgument(2),
                        java.util.UUID.randomUUID().toString(), java.time.Instant.now()));
        when(fenceStore.complete(any())).thenReturn(CacheFenceCompletion.COMPLETED);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(
                transactionManager, fenceStore, eventStore);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        coordinator.execute(new CacheMutation(CacheConsistencyMode.DURABLE, "users", "u1"), () -> {
            requiresNew.execute(status -> {
                try {
                    coordinator.execute(
                            new CacheMutation(CacheConsistencyMode.DURABLE, "users", "u1"),
                            () -> "inner", ignored -> { });
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
                return null;
            });
            return "outer";
        }, ignored -> { });

        verify(fenceStore, org.mockito.Mockito.times(2)).acquire(any(), any(), any());
        verify(eventStore, org.mockito.Mockito.times(2)).append(any());
    }

    @Test
    @DisplayName("事务启动失败时必须释放尚未由 Outbox 接管的围栏")
    void durableShouldReleaseFenceWhenTransactionCannotStart() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("tx unavailable"));
        RedisCacheFenceStore fenceStore = mock(RedisCacheFenceStore.class);
        CacheInvalidationEventStore eventStore = mock(CacheInvalidationEventStore.class);
        CacheFence fence = new CacheFence(
                "users", "u1", "event", "token", java.time.Instant.now());
        when(fenceStore.acquire(any(), any(), any())).thenReturn(fence);
        when(fenceStore.complete(fence)).thenReturn(CacheFenceCompletion.COMPLETED);
        SpringCacheMutationCoordinator coordinator = new SpringCacheMutationCoordinator(
                transactionManager, fenceStore, eventStore);

        assertThrows(IllegalStateException.class, () -> coordinator.execute(
                new CacheMutation(CacheConsistencyMode.DURABLE, "users", "u1"),
                () -> "business", ignored -> { }));

        verify(fenceStore).complete(fence);
        verify(eventStore, org.mockito.Mockito.never()).append(any());
    }

    /**
     * 不依赖数据库的最小事务管理器，只记录提交与回滚行为。
     */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager
            implements PlatformTransactionManager {

        private final List<String> events;

        private RecordingTransactionManager(List<String> events) {
            this.events = events;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 测试事务不需要真实资源。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }
    }
}
