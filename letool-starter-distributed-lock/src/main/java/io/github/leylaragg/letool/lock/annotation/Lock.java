package io.github.leylaragg.letool.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 在方法执行期间持有分布式锁。
 *
 * <p>{@link #key()} 支持方法参数模板。默认使用后端看门狗维护租约；只有明确知道业务
 * 最长执行时间时才应设置正数固定租约。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

    /** @return 业务锁 key，支持形如 {@code order:#{#orderId}} 的 SpEL 模板 */
    String key();

    /** @return 等待获取锁的最长时间，允许为零 */
    long waitTime() default 3;

    /**
     * @return 固定租约；默认 {@code -1} 表示使用后端看门狗，其他负数和零均无效
     */
    long leaseTime() default -1;

    /** @return 等待时间和固定租约的单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
