package io.github.leylaragg.letool.lock.aspect;

import io.github.leylaragg.letool.lock.annotation.Lock;
import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.tool.util.SpelUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;

/**
 * 将 {@link Lock} 方法调用转换为后端无关的锁请求。
 */
@Aspect
public class LockAspect {

    private final LockTemplate lockTemplate;

    /** @param lockTemplate 负责获取和关闭锁句柄的模板 */
    public LockAspect(LockTemplate lockTemplate) {
        this.lockTemplate = Objects.requireNonNull(lockTemplate, "lockTemplate must not be null");
    }

    /**
     * 解析实际方法参数中的锁 key，并在锁保护下调用目标方法。
     *
     * @param joinPoint 当前方法调用
     * @param lockAnn 当前方法上的锁注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法原始异常
     */
    @Around("@annotation(lockAnn)")
    public Object around(ProceedingJoinPoint joinPoint, Lock lockAnn) throws Throwable {
        Method method = mostSpecificMethod(joinPoint);
        String key = SpelUtil.evalMethodTemplate(
                lockAnn.key(), joinPoint.getTarget(), method, joinPoint.getArgs());
        Duration waitTime = toDuration(lockAnn.waitTime(), lockAnn.timeUnit());
        LockRequest request = lockAnn.leaseTime() == -1
                ? LockRequest.watchdog(key, waitTime)
                : LockRequest.fixedLease(
                        key, waitTime, toDuration(lockAnn.leaseTime(), lockAnn.timeUnit()));
        try {
            return lockTemplate.execute(request, () -> proceed(joinPoint));
        } catch (InvocationException exception) {
            throw exception.getCause();
        }
    }

    private static Method mostSpecificMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    private static Duration toDuration(long value, java.util.concurrent.TimeUnit unit) {
        return Duration.ofNanos(Objects.requireNonNull(unit, "timeUnit must not be null").toNanos(value));
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable exception) {
            throw new InvocationException(exception);
        }
    }

    /** 仅用于穿过不支持受检异常的 Supplier 边界，切面出口会恢复原始异常。 */
    private static final class InvocationException extends RuntimeException {
        private InvocationException(Throwable cause) {
            super(cause);
        }
    }
}
