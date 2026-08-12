package com.github.leyland.letool.cache.consistency;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * 基于 Spring 事务的缓存修改协调器。
 *
 * <p>业务动作在 REQUIRED 事务中执行，缓存动作通过事务同步回调延迟到数据库提交之后。
 * 数据库回滚时不会执行缓存动作。</p>
 */
public class SpringCacheMutationCoordinator implements CacheMutationCoordinator {

    /** Spring 编程式事务模板。 */
    private final TransactionTemplate transactionTemplate;
    /** DURABLE 模式使用的 Redis 围栏存储。 */
    private final RedisCacheFenceStore fenceStore;
    /** DURABLE 模式使用的数据库 Outbox。 */
    private final CacheInvalidationEventStore eventStore;

    /**
     * 创建事务协调器。
     *
     * @param transactionManager 业务数据库使用的事务管理器
     */
    public SpringCacheMutationCoordinator(PlatformTransactionManager transactionManager) {
        this(transactionManager, null, null);
    }

    /**
     * 创建同时支持 TRANSACTIONAL 与 DURABLE 的协调器。
     */
    public SpringCacheMutationCoordinator(
            PlatformTransactionManager transactionManager,
            RedisCacheFenceStore fenceStore,
            CacheInvalidationEventStore eventStore) {
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.fenceStore = fenceStore;
        this.eventStore = eventStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T execute(
            CacheMutation mutation,
            ThrowingSupplier<T> businessAction,
            Consumer<T> afterCommit) throws Throwable {
        Objects.requireNonNull(mutation, "缓存修改上下文不能为空");
        Objects.requireNonNull(businessAction, "业务动作不能为空");
        Objects.requireNonNull(afterCommit, "提交后缓存动作不能为空");
        if (mutation.mode() == CacheConsistencyMode.DURABLE) {
            return executeDurable(mutation, businessAction, afterCommit);
        }

        AtomicReference<Throwable> businessFailure = new AtomicReference<>();
        try {
            return transactionTemplate.execute(status -> {
                T result;
                try {
                    result = businessAction.get();
                } catch (Throwable throwable) {
                    businessFailure.set(throwable);
                    status.setRollbackOnly();
                    throw new BusinessActionException(throwable);
                }
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("缓存事务同步未启用");
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        afterCommit.accept(result);
                    }
                });
                return result;
            });
        } catch (BusinessActionException exception) {
            Throwable original = businessFailure.get();
            throw original == null ? exception.getCause() : original;
        }
    }

    private <T> T executeDurable(
            CacheMutation mutation,
            ThrowingSupplier<T> businessAction,
            Consumer<T> afterCommit) throws Throwable {
        if (fenceStore == null || eventStore == null) {
            throw new IllegalStateException("DURABLE 一致性协议尚未装配");
        }
        if (mutation.cacheName() == null || mutation.serializedKey() == null) {
            throw new IllegalArgumentException("DURABLE 模式必须提供缓存名称和业务 Key");
        }

        String eventId = UUID.randomUUID().toString();
        CacheFence fence = fenceStore.acquire(
                mutation.cacheName(), mutation.serializedKey(), eventId);
        AtomicReference<Throwable> businessFailure = new AtomicReference<>();
        AtomicReference<T> resultHolder = new AtomicReference<>();
        try {
            return transactionTemplate.execute(status -> {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("缓存事务同步未启用");
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            fenceStore.complete(fence);
                            afterCommit.accept(resultHolder.get());
                            eventStore.markCompleted(eventId);
                        } catch (Exception ignored) {
                            // 数据库已经提交，不能再把请求伪装成业务失败；Outbox 会负责后续重放。
                        }
                    }

                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus != TransactionSynchronization.STATUS_ROLLED_BACK) {
                            return;
                        }
                        try {
                            fenceStore.complete(fence);
                        } catch (Exception ignored) {
                            // 围栏带 TTL，且旧缓存已在建立围栏时删除；清理失败不会暴露事务前旧值。
                        }
                    }
                });
                T result;
                try {
                    result = businessAction.get();
                    resultHolder.set(result);
                    eventStore.append(CacheInvalidationEvent.pending(
                            eventId, mutation.cacheName(), mutation.serializedKey(),
                            fence.token(), fence.createdAt()));
                } catch (Throwable throwable) {
                    businessFailure.set(throwable);
                    status.setRollbackOnly();
                    throw new BusinessActionException(throwable);
                }
                return result;
            });
        } catch (BusinessActionException exception) {
            Throwable original = businessFailure.get();
            throw original == null ? exception.getCause() : original;
        }
    }

    /**
     * 仅用于让受检业务异常穿过 TransactionTemplate 的内部包装。
     */
    private static final class BusinessActionException extends RuntimeException {

        private BusinessActionException(Throwable cause) {
            super(cause);
        }
    }
}
