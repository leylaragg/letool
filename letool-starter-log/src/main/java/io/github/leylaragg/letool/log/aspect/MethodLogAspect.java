package io.github.leylaragg.letool.log.aspect;

import io.github.leylaragg.letool.log.annotation.MethodLog;
import io.github.leylaragg.letool.log.config.LogProperties;
import io.github.leylaragg.letool.log.trace.TraceContext;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 方法日志切面 —— 拦截 {@link MethodLog} 标记的方法，自动记录调用结果、耗时和异常。
 *
 * <h2>执行流程</h2>
 * <pre>
 *   1. 从目标实现方法解析注解和日志标题
 *   2. 按链路追踪配置获取或临时生成 TraceId
 *   3. 记录入参（可选）→ 使用可替换的 JsonCodec 序列化
 *   4. 执行目标方法 → 计时
 *   5. 记录出参（可选）→ 截断至 maxResultLength
 *   6. 捕获异常 → 记录完整异常堆栈并继续向上抛
 *   7. 清理由当前切面临时创建的 TraceId
 * </pre>
 */
@Aspect
public class MethodLogAspect {

    private static final Logger INTERNAL_LOG = LoggerFactory.getLogger(MethodLogAspect.class);

    private final JsonCodec jsonCodec;
    private final LogProperties properties;

    /**
     * 使用 Letool 默认 JSON 编解码器和日志配置创建方法日志切面。
     *
     * <p>该构造器主要用于用户直接声明替换 Bean；自动配置会优先使用依赖注入构造器。</p>
     */
    public MethodLogAspect() {
        this(Fastjson2JsonCodec.createDefault(), new LogProperties());
    }

    /**
     * 使用指定的 JSON 编解码器和日志配置创建方法日志切面。
     *
     * @param jsonCodec JSON 编解码器，不允许为 {@code null}
     * @param properties 日志模块配置，不允许为 {@code null}
     */
    public MethodLogAspect(JsonCodec jsonCodec, LogProperties properties) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 环绕拦截所有标注 {@link MethodLog} 的方法。
     *
     * <p>切面会从目标类解析最具体方法，兼容实现方法上声明注解的 JDK 接口代理。</p>
     *
     * @param joinPoint 当前被拦截的方法调用
     * @return 业务方法原始返回值
     * @throws Throwable 业务方法抛出的原始异常
     */
    @Around("@annotation(io.github.leylaragg.letool.log.annotation.MethodLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget() == null
                ? signature.getDeclaringType()
                : ClassUtils.getUserClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        MethodLog annotation = AnnotatedElementUtils.findMergedAnnotation(method, MethodLog.class);
        if (annotation == null) {
            // 理论上切点已确保注解存在；代理元数据异常时仍应保证业务方法可执行。
            INTERNAL_LOG.warn("未能从目标方法解析 MethodLog 注解，已跳过方法日志: {}.{}",
                    targetClass.getName(), method.getName());
            return joinPoint.proceed();
        }

        // 获取目标类专属 Logger，确保日志输出到正确的类名下
        Logger log = LoggerFactory.getLogger(targetClass);

        // 标题：优先用注解 value，未设置则用方法名
        String title = annotation.value().isBlank() ? method.getName() : annotation.value();
        String targetName = targetClass.getSimpleName();

        String traceId = TraceContext.getTraceId();
        // 仅在链路追踪开启且上下文缺失时生成 TraceId，关闭追踪后不绕过用户配置。
        boolean traceIdCreatedByAspect = properties.getTrace().isEnabled()
                && (traceId == null || traceId.isBlank());
        if (traceIdCreatedByAspect) {
            traceId = TraceContext.getOrGenerate();
        } else if (traceId == null || traceId.isBlank()) {
            traceId = "-";
        }

        // ==== 1. 记录入参 ====
        if (annotation.logArgs()) {
            String arguments = formatValue(joinPoint.getArgs(), annotation.maxArgsLength());
            log.info("[{}] {} 调用入参: {}.{}，入参: {}", traceId, title,
                    targetName, method.getName(), arguments);
        }

        long startNanos = System.nanoTime();
        try {
            // ==== 2. 执行目标方法 ====
            Object result = joinPoint.proceed();
            long duration = elapsedMillis(startNanos);

            // ==== 3. 记录出参（仅在成功时）====
            if (annotation.logResult()) {
                String resultStr = formatValue(result, annotation.maxResultLength());
                log.info("[{}] {} 执行成功: {}.{}，出参: {}，耗时: {}ms", traceId, title,
                        targetName, method.getName(), resultStr, duration);
            } else {
                log.info("[{}] {} 执行成功: {}.{}，耗时: {}ms", traceId, title,
                        targetName, method.getName(), duration);
            }
            return result;
        } catch (Throwable e) {
            // ==== 4. 记录异常（不吞异常，继续向上抛给调用方）====
            long duration = elapsedMillis(startNanos);
            if (annotation.logException()) {
                log.error("[{}] {} 执行失败: {}.{}，耗时: {}ms", traceId, title,
                        targetName, method.getName(), duration, e);
            }
            throw e;
        } finally {
            if (traceIdCreatedByAspect) {
                TraceContext.clear();
            }
        }
    }

    /**
     * 使用可替换的 JSON 编解码器格式化日志值，并对超长文本执行截断。
     *
     * @param value 待格式化的入参或出参
     * @param maxLength 最大记录字符数
     * @return 可写入日志的 JSON 文本
     */
    private String formatValue(Object value, int maxLength) {
        if (maxLength < 1) {
            INTERNAL_LOG.warn("方法日志最大记录长度必须大于零，当前配置: {}", maxLength);
            return "<长度配置无效>";
        }
        try {
            String json = jsonCodec.write(value);
            if (json == null) {
                return "null";
            }
            if (json.length() > maxLength) {
                return json.substring(0, maxLength) + "...";
            }
            return json;
        } catch (RuntimeException exception) {
            // 日志辅助能力不得改变业务结果，失败时只记录数据类型，不输出原始敏感数据。
            String valueType = value == null ? "null" : value.getClass().getName();
            INTERNAL_LOG.warn("序列化方法日志数据失败，已使用安全占位文本，数据类型: {}",
                    valueType, exception);
            return "<序列化失败>";
        }
    }

    /**
     * 根据单调时钟计算已经经过的毫秒数，避免系统时间回拨影响耗时统计。
     *
     * @param startNanos 起始纳秒时间
     * @return 已经过的毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
