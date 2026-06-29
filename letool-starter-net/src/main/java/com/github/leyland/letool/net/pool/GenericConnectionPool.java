package com.github.leyland.letool.net.pool;

import com.github.leyland.letool.net.exception.NetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通用连接池 —— 基于 {@link BlockingQueue} 实现的轻量级对象池.
 *
 * <p>无需依赖 Apache Commons Pool2，可管理任意类型的连接对象（Socket、HttpClient 等）.
 * 通过 {@link ConnectionFactory} 接口创建和销毁连接.</p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * GenericConnectionPool<Socket> pool = new GenericConnectionPool<>(
 *     PoolConfig.builder().minIdle(2).maxTotal(10).build(),
 *     () -> new Socket(host, port)
 * );
 * Socket conn = pool.borrowObject();
 * try { ... } finally { pool.returnObject(conn); }
 * }</pre>
 *
 * @param <T> 池化的对象类型
 * @author leyland
 * @since 2.0.0
 */
public class GenericConnectionPool<T> {

    private static final Logger log = LoggerFactory.getLogger(GenericConnectionPool.class);

    // ======================== 字段 ========================

    /** 池配置 */
    private final PoolConfig config;

    /** 连接工厂，用于创建和销毁连接 */
    private final ConnectionFactory<T> factory;

    /** 空闲连接队列 */
    private final BlockingQueue<T> idleQueue;

    /** 当前活跃连接数（含借出和空闲） */
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /** 创建连接的互斥锁（防止并发创建超出 maxTotal） */
    private final ReentrantLock createLock = new ReentrantLock();

    /** 是否已关闭 */
    private volatile boolean closed = false;

    // ======================== 构造器 ========================

    /**
     * 构造连接池.
     *
     * @param config  池配置（最小空闲、最大总数等）
     * @param factory 连接工厂，定义如何创建和销毁连接对象
     */
    public GenericConnectionPool(PoolConfig config, ConnectionFactory<T> factory) {
        this.config = config;
        this.factory = factory;
        this.idleQueue = new LinkedBlockingQueue<>(config.getMaxTotal());
    }

    // ======================== 借用 / 归还 ========================

    /**
     * 从池中借用一个连接对象.
     *
     * <p>优先从空闲队列获取；若无空闲且未达上限则创建新连接；
     * 若已达上限则等待直到有连接归还.</p>
     *
     * @return 可用的连接对象
     * @throws NetException 如果等待超时或创建连接失败
     */
    public T borrowObject() {
        checkNotClosed();
        T obj = idleQueue.poll();
        if (obj != null) {
            // 校验连接有效性
            if (factory.validateObject(obj)) {
                return obj;
            } else {
                // 失效连接，销毁
                factory.destroyObject(obj);
                activeCount.decrementAndGet();
            }
        }
        // 尝试创建新连接
        if (activeCount.get() < config.getMaxTotal()) {
            createLock.lock();
            try {
                if (activeCount.get() < config.getMaxTotal()) {
                    T newObj = factory.createObject();
                    activeCount.incrementAndGet();
                    return newObj;
                }
            } catch (Exception e) {
                throw new NetException("Failed to create pooled object", e);
            } finally {
                createLock.unlock();
            }
        }
        // 已满，等待归还
        try {
            obj = idleQueue.poll(config.getMaxWaitMs(), TimeUnit.MILLISECONDS);
            if (obj == null) {
                throw new NetException("Connection pool exhausted, maxTotal=" + config.getMaxTotal()
                        + ", waited " + config.getMaxWaitMs() + "ms");
            }
            if (factory.validateObject(obj)) {
                return obj;
            } else {
                factory.destroyObject(obj);
                activeCount.decrementAndGet();
                throw new NetException("Pooled object validation failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetException("Interrupted while waiting for pooled object", e);
        }
    }

    /**
     * 将连接对象归还到池中.
     *
     * @param obj 待归还的连接对象
     */
    public void returnObject(T obj) {
        if (obj == null || closed) {
            return;
        }
        if (!idleQueue.offer(obj)) {
            // 队列已满（异常情况），直接销毁
            factory.destroyObject(obj);
            activeCount.decrementAndGet();
            log.warn("Idle queue full, destroyed returned object");
        }
    }

    /**
     * 将连接对象标记为无效并移出池.
     *
     * @param obj 失效的连接对象
     */
    public void invalidateObject(T obj) {
        if (obj == null) {
            return;
        }
        factory.destroyObject(obj);
        activeCount.decrementAndGet();
    }

    // ======================== 生命周期 ========================

    /**
     * 关闭连接池，销毁所有空闲连接.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        T obj;
        while ((obj = idleQueue.poll()) != null) {
            factory.destroyObject(obj);
            activeCount.decrementAndGet();
        }
        log.info("Connection pool closed, remaining active count: {}", activeCount.get());
    }

    /**
     * 获取当前活跃连接数.
     *
     * @return 活跃连接数（含借出和空闲）
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * 获取当前空闲连接数.
     *
     * @return 空闲连接数
     */
    public int getIdleCount() {
        return idleQueue.size();
    }

    // ======================== 内部方法 ========================

    /**
     * 校验池是否已关闭.
     *
     * @throws NetException 如果池已关闭
     */
    private void checkNotClosed() {
        if (closed) {
            throw new NetException("Connection pool has been closed");
        }
    }

    // ======================== 连接工厂接口 ========================

    /**
     * 连接工厂接口 —— 定义连接对象的创建、验证和销毁逻辑.
     *
     * @param <T> 连接对象类型
     */
    public interface ConnectionFactory<T> {

        /**
         * 创建新连接.
         *
         * @return 新创建的连接对象
         * @throws Exception 创建失败
         */
        T createObject() throws Exception;

        /**
         * 验证连接对象是否有效.
         *
         * @param obj 待验证的连接对象
         * @return {@code true} 如果连接仍有效
         */
        default boolean validateObject(T obj) {
            return obj != null;
        }

        /**
         * 销毁连接对象（释放底层资源）.
         *
         * @param obj 待销毁的连接对象
         */
        void destroyObject(T obj);
    }
}
