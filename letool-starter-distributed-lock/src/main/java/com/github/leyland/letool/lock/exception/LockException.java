package com.github.leyland.letool.lock.exception;

/**
 * 分布式锁模块的统一异常类。
 *
 * <p>用于表示分布式锁操作中出现的各类异常，包括但不限于：</p>
 * <ul>
 *   <li><b>获取锁超时</b>：在指定等待时间内未能获取到锁</li>
 *   <li><b>释放锁失败</b>：释放锁时发生 Redis 通信异常或 token 不匹配</li>
 *   <li><b>锁操作中断</b>：等待锁的过程中线程被中断</li>
 * </ul>
 *
 * <p>继承自 {@link RuntimeException}，属于非受检异常，调用方可根据需要选择捕获。</p>
 *
 * @author leyland
 * @since 1.0.0
 * @see com.github.leyland.letool.lock.core.LockTemplate
 * @see com.github.leyland.letool.lock.core.DistributedLock
 */
public class LockException extends RuntimeException {

    /**
     * 使用错误消息构造异常。
     *
     * @param message 描述错误详情的消息
     */
    public LockException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和根因构造异常。
     *
     * @param message 描述错误详情的消息
     * @param cause   导致此异常的原始异常
     */
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}
