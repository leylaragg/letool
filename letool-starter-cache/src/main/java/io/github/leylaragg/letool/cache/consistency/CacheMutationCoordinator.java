package io.github.leylaragg.letool.cache.consistency;

import java.util.function.Consumer;

/**
 * 协调业务数据库事务与提交后缓存动作。
 */
public interface CacheMutationCoordinator {

    /**
     * 在指定一致性模式下执行业务修改。
     *
     * @param mode 一致性模式
     * @param businessAction 需要参与事务的业务动作
     * @param afterCommit 数据库提交后执行的缓存动作
     * @param <T> 业务返回类型
     * @return 业务返回值
     * @throws Throwable 业务动作或提交后动作抛出的原始异常
     */
    <T> T execute(
            CacheMutation mutation,
            ThrowingSupplier<T> businessAction,
            Consumer<T> afterCommit) throws Throwable;

    /**
     * 兼容只传一致性模式的调用；DURABLE 模式必须使用包含缓存身份的重载。
     */
    default <T> T execute(
            CacheConsistencyMode mode,
            ThrowingSupplier<T> businessAction,
            Consumer<T> afterCommit) throws Throwable {
        return execute(CacheMutation.transactional(mode), businessAction, afterCommit);
    }

    /**
     * 允许抛出受检异常的业务动作。
     *
     * @param <T> 返回类型
     */
    @FunctionalInterface
    interface ThrowingSupplier<T> {

        /**
         * 执行业务动作。
         *
         * @return 业务返回值
         * @throws Throwable 业务原始异常
         */
        T get() throws Throwable;
    }
}
