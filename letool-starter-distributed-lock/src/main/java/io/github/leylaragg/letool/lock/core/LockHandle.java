package io.github.leylaragg.letool.lock.core;

/**
 * 当前线程成功获取的分布式锁句柄。
 *
 * <p>句柄绑定具体的一次获取操作，关闭时只能释放该次操作持有的锁，避免按 key
 * 二次查找导致误释放其他线程后来获得的锁。</p>
 */
public interface LockHandle extends AutoCloseable {

    /** @return 调用方提交的业务锁 key */
    String key();

    /** @return 当前线程是否仍然持有该句柄对应的锁 */
    boolean isHeldByCurrentThread();

    /** 释放当前句柄持有的锁；重复关闭应安全退让。 */
    @Override
    void close();
}
