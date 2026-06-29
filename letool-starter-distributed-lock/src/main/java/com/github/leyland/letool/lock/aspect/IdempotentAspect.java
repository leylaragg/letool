package com.github.leyland.letool.lock.aspect;

import com.github.leyland.letool.lock.annotation.Idempotent;
import com.github.leyland.letool.lock.idempotent.IdempotentService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Idempotent} 注解的 AOP 切面处理器。
 *
 * <p>拦截所有标注了 {@code @Idempotent} 注解的方法，自动完成以下流程：</p>
 * <ol>
 *   <li>从注解中提取 key 和 ttl 参数</li>
 *   <li>委托 {@link IdempotentService#execute} 执行幂等检查并回调业务方法</li>
 *   <li>重复请求（idempotentService 返回 null）时直接返回 null</li>
 *   <li>首次请求正常执行，返回值由 idempotentService 透传回来</li>
 * </ol>
 *
 * <p>该切面在 {@link com.github.leyland.letool.lock.config.LockAutoConfiguration}
 * 中自动注册，无需手动配置。</p>
 *
 * <h3>执行链</h3>
 * <pre>{@code
 * @Idempotent 注解 → IdempotentAspect（本类）→ IdempotentService → Redis Lua SET NX EX
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 * @see Idempotent
 * @see IdempotentService
 */
@Aspect
public class IdempotentAspect {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    // ======================== 依赖 ========================

    /** 幂等服务，负责实际的幂等检查逻辑 */
    private final IdempotentService idempotentService;

    // ======================== 构造方法 ========================

    /**
     * 构造幂等切面。
     *
     * @param idempotentService 幂等服务实例（不可为 null）
     */
    public IdempotentAspect(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    // ======================== 环绕通知 ========================

    /**
     * 环绕通知：处理标注了 {@link Idempotent @Idempotent} 的方法。
     *
     * <p>将方法的执行包装为 {@link java.util.function.Supplier} 传入
     * {@link IdempotentService#execute}。返回值处理规则：</p>
     * <ul>
     *   <li>首次执行：返回业务方法的实际返回值</li>
     *   <li>重复执行：返回 {@code null}</li>
     *   <li>业务异常：idempotentService 会将异常包装为 {@link RuntimeException} 返回，
     *       此处解包后重新抛出原始异常</li>
     * </ul>
     *
     * @param joinPoint 切点连接点，代表被拦截的方法调用
     * @param ann       方法上标注的 {@link Idempotent} 注解实例
     * @return 目标方法的返回值，重复请求时返回 {@code null}
     * @throws Throwable 目标方法执行异常
     */
    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent ann) throws Throwable {
        String key = ann.key();
        Object result = idempotentService.execute(key, ann.ttl(), () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        // 如果业务方法抛出异常，idempotentService 会将其包装为 RuntimeException 返回
        if (result instanceof RuntimeException) {
            throw ((RuntimeException) result).getCause();
        }
        return result;
    }
}
