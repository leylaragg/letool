package io.github.leylaragg.letool.lock.core;

import java.util.Optional;

/**
 * 分布式锁后端契约。
 *
 * <p>接口只描述获取句柄和查询状态，不泄露 Redis、ZooKeeper 等实现细节。</p>
 */
public interface DistributedLock {

    /**
     * 按请求尝试获取锁。
     *
     * @param request 已校验的锁请求
     * @return 成功时返回绑定当前所有权的句柄；超时返回空
     */
    Optional<LockHandle> tryAcquire(LockRequest request);

    /**
     * 查询业务 key 当前是否被任意调用方持有。
     *
     * @param key 业务锁 key
     * @return {@code true} 表示锁处于持有状态
     */
    boolean isLocked(String key);
}
