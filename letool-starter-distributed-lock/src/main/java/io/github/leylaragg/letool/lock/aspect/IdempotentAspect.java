package io.github.leylaragg.letool.lock.aspect;

import io.github.leylaragg.letool.lock.annotation.Idempotent;
import io.github.leylaragg.letool.lock.idempotent.IdempotentService;
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
 * 将 {@link Idempotent} 方法调用转换为后端无关的原子占位操作。
 */
@Aspect
public class IdempotentAspect {

    private final IdempotentService idempotentService;

    /** @param idempotentService 幂等执行服务 */
    public IdempotentAspect(IdempotentService idempotentService) {
        this.idempotentService = Objects.requireNonNull(
                idempotentService, "idempotentService must not be null");
    }

    /**
     * 解析实际方法参数中的幂等 key，并在首次占位成功时执行目标方法。
     *
     * @param joinPoint 当前方法调用
     * @param annotation 当前方法上的幂等注解
     * @return 首次调用的业务结果；重复调用返回 {@code null}
     * @throws Throwable 目标方法原始异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent annotation) throws Throwable {
        Method method = mostSpecificMethod(joinPoint);
        String key = SpelUtil.evalMethodTemplate(
                annotation.key(), joinPoint.getTarget(), method, joinPoint.getArgs());
        try {
            return idempotentService.execute(
                    key, Duration.ofSeconds(annotation.ttl()), () -> proceed(joinPoint));
        } catch (InvocationException exception) {
            throw exception.getCause();
        }
    }

    private static Method mostSpecificMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable exception) {
            throw new InvocationException(exception);
        }
    }

    /** 仅用于跨越 Supplier 的受检异常边界，切面出口会恢复原始异常。 */
    private static final class InvocationException extends RuntimeException {
        private InvocationException(Throwable cause) {
            super(cause);
        }
    }
}
