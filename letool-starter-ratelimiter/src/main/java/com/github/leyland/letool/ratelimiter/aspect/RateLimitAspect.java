package com.github.leyland.letool.ratelimiter.aspect;

import com.github.leyland.letool.ratelimiter.annotation.CircuitBreak;
import com.github.leyland.letool.ratelimiter.annotation.RateLimit;
import com.github.leyland.letool.ratelimiter.circuit.CircuitBreaker;
import com.github.leyland.letool.ratelimiter.circuit.DefaultCircuitBreaker;
import com.github.leyland.letool.ratelimiter.core.RateLimitTemplate;
import com.github.leyland.letool.ratelimiter.exception.RateLimitException;
import com.github.leyland.letool.tool.util.SpelUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流和熔断注解的 AOP 切面处理器。
 *
 * <p>拦截所有标注了 {@link RateLimit @RateLimit} 或 {@link CircuitBreak @CircuitBreak}
 * 注解的方法，自动完成以下流程：</p>
 *
 * <h3>{@code @RateLimit} 处理流程</h3>
 * <ol>
 *   <li>解析 SpEL 表达式，获取限流 key</li>
 *   <li>通过 {@link RateLimitTemplate#tryAcquire} 检查许可</li>
 *   <li>若被拒绝：
 *     <ul>
 *       <li>若指定了 {@code fallbackMethod}，反射调用回退方法</li>
 *       <li>否则抛出 {@link RateLimitException}</li>
 *     </ul>
 *   </li>
 *   <li>若允许：执行目标方法</li>
 * </ol>
 *
 * <h3>{@code @CircuitBreak} 处理流程</h3>
 * <ol>
 *   <li>获取或创建对应名称的熔断器实例</li>
 *   <li>检查熔断器是否允许通过（{@link CircuitBreaker#isAllowed()}）</li>
 *   <li>若被拒绝：
 *     <ul>
 *       <li>若指定了 {@code fallbackMethod}，反射调用回退方法</li>
 *       <li>否则抛出 {@link RateLimitException}</li>
 *     </ul>
 *   </li>
 *   <li>若允许：执行目标方法，根据结果记录成功/失败</li>
 * </ol>
 *
 * <p>该切面在 {@link com.github.leyland.letool.ratelimiter.config.RateLimiterAutoConfiguration}
 * 中自动注册，无需手动配置。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimit
 * @see CircuitBreak
 * @see RateLimitTemplate
 */
@Aspect
public class RateLimitAspect {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    // ======================== 依赖 ========================

    /** 限流模板，负责实际的许可获取 */
    private final RateLimitTemplate rateLimitTemplate;

    /** 熔断器实例缓存，key 为熔断器名称 */
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /** 全局默认熔断器配置 */
    private final CircuitBreakerConfig defaultCircuitBreakerConfig;

    // ======================== 构造方法 ========================

    /**
     * 构造限流/熔断切面。
     *
     * @param rateLimitTemplate           限流模板实例
     * @param defaultCircuitBreakerConfig 默认熔断器配置
     */
    public RateLimitAspect(RateLimitTemplate rateLimitTemplate,
                           CircuitBreakerConfig defaultCircuitBreakerConfig) {
        this.rateLimitTemplate = rateLimitTemplate;
        this.defaultCircuitBreakerConfig = defaultCircuitBreakerConfig;
    }

    // ======================== @RateLimit 环绕通知 ========================

    /**
     * 环绕通知：处理标注了 {@link RateLimit @RateLimit} 的方法。
     *
     * <p>解析注解中的 SpEL 表达式获取限流 key，委托 {@link RateLimitTemplate}
     * 进行许可检查。被拒绝时根据是否配置 {@code fallbackMethod} 决定降级策略。</p>
     *
     * @param joinPoint 切点连接点
     * @param rateLimit 方法上标注的 {@link RateLimit} 注解
     * @return 目标方法返回值 或 降级方法返回值
     * @throws Throwable 目标方法或降级方法执行异常，或限流异常
     */
    @Around("@annotation(rateLimit)")
    public Object aroundRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. 解析 SpEL 表达式获取限流 key
        String key = parseKey(joinPoint, rateLimit.key());
        int permits = rateLimit.permits();

        log.debug("@RateLimit check: key={}, permits={}, algorithm={}",
                key, permits, rateLimit.algorithm());

        // 2. 执行许可检查
        boolean allowed = rateLimitTemplate.tryAcquire(key, permits);

        if (!allowed) {
            log.warn("@RateLimit rejected: key={}, permits={}, method={}",
                    key, permits, getMethodName(joinPoint));

            // 3. 被拒绝：尝试降级
            if (!rateLimit.fallbackMethod().isEmpty()) {
                return invokeFallbackMethod(joinPoint, rateLimit.fallbackMethod());
            }
            throw new RateLimitException(
                    "Rate limit exceeded: key=" + key + ", permits=" + permits);
        }

        // 4. 允许通过
        return joinPoint.proceed();
    }

    // ======================== @CircuitBreak 环绕通知 ========================

    /**
     * 环绕通知：处理标注了 {@link CircuitBreak @CircuitBreak} 的方法。
     *
     * <p>获取或创建熔断器实例，检查是否允许通过。执行后根据结果记录成功或失败。
     * 被拒绝时根据是否配置 {@code fallbackMethod} 决定降级策略。</p>
     *
     * @param joinPoint    切点连接点
     * @param circuitBreak 方法上标注的 {@link CircuitBreak} 注解
     * @return 目标方法返回值 或 降级方法返回值
     * @throws Throwable 目标方法或降级方法执行异常，或熔断异常
     */
    @Around("@annotation(circuitBreak)")
    public Object aroundCircuitBreak(ProceedingJoinPoint joinPoint, CircuitBreak circuitBreak) throws Throwable {
        String name = circuitBreak.name();

        // 1. 获取或创建熔断器
        CircuitBreaker breaker = getOrCreateCircuitBreaker(circuitBreak);

        // 2. 检查是否允许通过
        if (!breaker.isAllowed()) {
            log.warn("@CircuitBreak rejected: name={}, state={}, method={}",
                    name, breaker.getState(), getMethodName(joinPoint));

            // 被拒绝：尝试降级
            if (!circuitBreak.fallbackMethod().isEmpty()) {
                return invokeFallbackMethod(joinPoint, circuitBreak.fallbackMethod());
            }
            throw new RateLimitException(
                    "Circuit breaker is OPEN: name=" + name + ", state=" + breaker.getState());
        }

        // 3. 执行目标方法
        try {
            Object result = joinPoint.proceed();
            breaker.recordSuccess();
            return result;
        } catch (Throwable e) {
            breaker.recordFailure();
            throw e;
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 解析 SpEL 表达式获取限流 key。
     *
     * <p>从连接点获取方法参数，构建 SpEL 上下文变量，然后对表达式求值。</p>
     *
     * @param joinPoint    切点连接点
     * @param keyExpression SpEL 表达式（来自 @RateLimit 的 key 属性）
     * @return 解析后的限流 key 字符串
     */
    private String parseKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        // 如果表达式中不含 #，说明是固定字符串，直接返回
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            Map<String, Object> variables = new HashMap<>();
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    variables.put(parameterNames[i], args[i]);
                }
            }

            // 将 SpEL 表达式解析为字符串
            Object value = SpelUtil.eval(keyExpression, null, variables);
            return value != null ? value.toString() : keyExpression;
        } catch (Exception e) {
            log.error("Failed to parse SpEL expression: {}, using raw string as key", keyExpression, e);
            return keyExpression;  // 解析失败时降级使用原始字符串
        }
    }

    /**
     * 通过反射调用指定名称的降级方法。
     *
     * <p>降级方法必须与标注方法在同一个类中，且参数签名完全一致。</p>
     *
     * @param joinPoint      切点连接点
     * @param fallbackMethod 降级方法名
     * @return 降级方法的返回值
     * @throws Throwable 降级方法执行异常
     */
    private Object invokeFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackMethod) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        Method method = target.getClass().getDeclaredMethod(fallbackMethod, signature.getParameterTypes());
        method.setAccessible(true);

        log.debug("Invoking fallback method: {}", fallbackMethod);
        return method.invoke(target, args);
    }

    /**
     * 获取或创建指定名称的熔断器实例。
     *
     * <p>使用 {@link ConcurrentHashMap#computeIfAbsent} 保证线程安全的惰性初始化。
     * 优先使用注解中的配置，注解未指定的使用全局默认配置。</p>
     *
     * @param circuitBreak 熔断注解
     * @return 熔断器实例
     */
    private CircuitBreaker getOrCreateCircuitBreaker(CircuitBreak circuitBreak) {
        return circuitBreakers.computeIfAbsent(circuitBreak.name(), name -> {
            double failureThreshold = circuitBreak.failureThreshold() != 0.5
                    ? circuitBreak.failureThreshold()
                    : defaultCircuitBreakerConfig.failureThreshold();

            int windowSize = circuitBreak.windowSize() != 60
                    ? circuitBreak.windowSize()
                    : defaultCircuitBreakerConfig.windowSize();

            int recoveryTimeout = circuitBreak.recoveryTimeout() != 60
                    ? circuitBreak.recoveryTimeout()
                    : defaultCircuitBreakerConfig.recoveryTimeout();

            int halfOpenMaxRequests = 3;  // 使用全局配置的默认值

            log.info("Creating CircuitBreaker: name={}, failureThreshold={}, windowSize={}, "
                            + "recoveryTimeout={}, halfOpenMaxRequests={}",
                    name, failureThreshold, windowSize, recoveryTimeout, halfOpenMaxRequests);

            return new DefaultCircuitBreaker(name, failureThreshold, windowSize,
                    recoveryTimeout, halfOpenMaxRequests);
        });
    }

    /**
     * 获取方法签名（用于日志输出）。
     *
     * @param joinPoint 切点连接点
     * @return "类名.方法名" 格式的方法标识
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    // ======================== 内部类：熔断器默认配置 ========================

    /**
     * 全局默认熔断器配置的值对象。
     *
     * <p>当 {@link CircuitBreak @CircuitBreak} 注解中未指定某些参数时，
     * 使用此配置中的全局默认值。</p>
     */
    public static class CircuitBreakerConfig {

        private final double failureThreshold;
        private final int windowSize;
        private final int recoveryTimeout;
        private final int halfOpenMaxRequests;

        /**
         * 创建熔断器默认配置。
         *
         * @param failureThreshold     失败率阈值（0~1）
         * @param windowSize           统计窗口大小（秒）
         * @param recoveryTimeout      恢复超时（秒）
         * @param halfOpenMaxRequests  半开状态最大试探请求数
         */
        public CircuitBreakerConfig(double failureThreshold, int windowSize,
                                     int recoveryTimeout, int halfOpenMaxRequests) {
            this.failureThreshold = failureThreshold;
            this.windowSize = windowSize;
            this.recoveryTimeout = recoveryTimeout;
            this.halfOpenMaxRequests = halfOpenMaxRequests;
        }

        public double failureThreshold() { return failureThreshold; }
        public int windowSize() { return windowSize; }
        public int recoveryTimeout() { return recoveryTimeout; }
        public int halfOpenMaxRequests() { return halfOpenMaxRequests; }
    }
}
