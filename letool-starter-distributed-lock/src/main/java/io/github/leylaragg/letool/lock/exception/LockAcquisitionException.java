package io.github.leylaragg.letool.lock.exception;

/**
 * 表示在等待时间内未能成功获得分布式锁。
 *
 * <p>该异常用于区分锁获取失败与业务回调、锁释放阶段产生的其他锁异常。</p>
 */
public class LockAcquisitionException extends LockException {

    /**
     * 使用锁获取失败消息构造异常。
     *
     * @param message 锁获取失败的上下文消息
     */
    public LockAcquisitionException(String message) {
        super(message);
    }
}
