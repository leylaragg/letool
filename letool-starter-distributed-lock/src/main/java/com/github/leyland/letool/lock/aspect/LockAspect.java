package com.github.leyland.letool.lock.aspect;

import com.github.leyland.letool.lock.annotation.Lock;
import com.github.leyland.letool.lock.core.LockTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Lock} 注解的 AOP 切面处理器。
 *
 * <p>拦截所有标注了 {@code @Lock} 注解的方法，自动完成以下流程：</p>
 * <ol>
 *   <li>从注解中提取 key、waitTime、leaseTime、timeUnit 参数</li>
 *   <li>通过 {@link LockTemplate#execute} 获取锁并执行业务方法</li>
 *   <li>在 finally 中自动释放锁</li>
 * </ol>
 *
 * <p>该切面在 {@link com.github.leyland.letool.lock.config.LockAutoConfiguration}
 * 中自动注册，无需手动配置。</p>
 *
 * <h3>执行链</h3>
 * <pre>{@code
 * @Lock 注解 → LockAspect（本类）→ LockTemplate → DistributedLock → Redis Lua 脚本
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 * @see Lock
 * @see LockTemplate
 */
@Aspect
public class LockAspect {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(LockAspect.class);

    // ======================== 依赖 ========================

    /** 锁操作模板，负责实际的加锁/释放逻辑 */
    private final LockTemplate lockTemplate;

    // ======================== 构造方法 ========================

    /**
     * 构造锁切面。
     *
     * @param lockTemplate 锁模板实例（不可为 null）
     */
    public LockAspect(LockTemplate lockTemplate) {
        this.lockTemplate = lockTemplate;
    }

    // ======================== 环绕通知 ========================

    /**
     * 环绕通知：处理标注了 {@link Lock @Lock} 的方法。
     *
     * <p>从 {@code lockAnn} 中提取锁参数，委托 {@link LockTemplate#execute}
     * 在锁保护下执行目标方法。若业务方法抛出已检查异常，会包装为
     * {@link RuntimeException} 抛出，由调用方决定如何处理。</p>
     *
     * @param joinPoint 切点连接点，代表被拦截的方法调用
     * @param lockAnn   方法上标注的 {@link Lock} 注解实例
     * @return 目标方法的返回值
     * @throws Throwable 目标方法执行异常或锁获取失败异常
     */
    @Around("@annotation(lockAnn)")
    public Object around(ProceedingJoinPoint joinPoint, Lock lockAnn) throws Throwable {
        String key = lockAnn.key();
        log.debug("Acquiring lock: {}", key);
        return lockTemplate.execute(key, lockAnn.waitTime(), lockAnn.leaseTime(),
                lockAnn.timeUnit(), () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
