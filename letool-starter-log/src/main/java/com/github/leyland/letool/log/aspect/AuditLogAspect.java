package com.github.leyland.letool.log.aspect;

import com.github.leyland.letool.log.annotation.AuditLog;
import com.github.leyland.letool.log.audit.AuditContext;
import com.github.leyland.letool.log.audit.AuditContextProvider;
import com.github.leyland.letool.log.audit.AuditLogEvent;
import com.github.leyland.letool.log.audit.AuditLogService;
import com.github.leyland.letool.log.trace.TraceContext;
import com.github.leyland.letool.tool.json.JsonCodec;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 将 {@link AuditLog} 标注的方法调用转换为结构化审计事件。
 *
 * <p>切面只负责采集与组装事件，持久化行为统一委托给 {@link AuditLogService}。
 * 审计上下文、SpEL 或序列化失败均不会改变业务方法结果；业务异常会原样向上抛出。</p>
 */
@Aspect
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAIL";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;

    private final AuditLogService auditLogService;
    private final JsonCodec jsonCodec;
    private final AuditContextProvider contextProvider;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    /**
     * 创建审计日志切面。
     *
     * @param auditLogService 审计事件输出服务
     * @param jsonCodec 请求参数序列化使用的 JSON 编解码器
     * @param contextProvider 当前调用方上下文提供器
     * @throws NullPointerException 任一依赖为 {@code null} 时抛出
     */
    public AuditLogAspect(
            AuditLogService auditLogService,
            JsonCodec jsonCodec,
            AuditContextProvider contextProvider) {
        this.auditLogService = Objects.requireNonNull(
                auditLogService, "auditLogService must not be null");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.contextProvider = Objects.requireNonNull(
                contextProvider, "contextProvider must not be null");
    }

    /**
     * 执行业务方法，并在结束后记录成功或失败审计事件。
     *
     * @param joinPoint 当前被拦截的方法调用
     * @param annotation 当前方法上的审计日志注解
     * @return 业务方法原始返回值
     * @throws Throwable 业务方法抛出的原始异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog annotation) throws Throwable {
        Method method = resolveTargetMethod(joinPoint);
        Object[] arguments = joinPoint.getArgs();
        AuditContext context = resolveContext(method);
        String businessNumber = resolveBusinessNumber(annotation.bizNo(), method, arguments);
        String requestBody = serializeArguments(
                annotation.logRequestBody(), annotation.maxBodyLength(), method, arguments);
        long startNanos = System.nanoTime();
        Throwable businessFailure = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            businessFailure = throwable;
            throw throwable;
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            int durationMs = elapsedMillis > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) elapsedMillis;
            AuditLogEvent event = AuditLogEvent.builder()
                    .traceId(TraceContext.getTraceId())
                    .operator(context.operator())
                    .operation(annotation.operation())
                    .type(annotation.type())
                    .bizNo(businessNumber)
                    .result(businessFailure == null ? SUCCESS : FAILURE)
                    .ip(context.clientIp())
                    .userAgent(context.userAgent())
                    .durationMs(durationMs)
                    .requestBody(requestBody)
                    .errorMessage(resolveErrorMessage(businessFailure))
                    .build();
            recordSafely(event, method);
        }
    }

    /**
     * 获取代理目标类上最具体的方法，保证参数名称与 SpEL 解析一致。
     *
     * @param joinPoint 当前方法调用
     * @return 目标类上最具体的方法
     */
    private Method resolveTargetMethod(ProceedingJoinPoint joinPoint) {
        Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        return target == null
                ? signatureMethod
                : AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());
    }

    /**
     * 安全获取当前调用方上下文。
     *
     * @param method 当前业务方法
     * @return 可安全使用的审计上下文
     */
    private AuditContext resolveContext(Method method) {
        try {
            AuditContext context = contextProvider.getCurrentContext();
            return context == null ? AuditContext.empty() : context;
        } catch (RuntimeException exception) {
            log.warn("获取审计上下文失败，方法={}", method.toGenericString(), exception);
            return AuditContext.empty();
        }
    }

    /**
     * 使用 Spring SpEL 从方法参数中解析业务编号。
     *
     * @param expression 注解配置的 SpEL
     * @param method 当前业务方法
     * @param arguments 当前方法参数
     * @return 业务编号；未配置、结果为空或解析失败时返回 {@code null}
     */
    private String resolveBusinessNumber(
            String expression,
            Method method,
            Object[] arguments) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodBasedEvaluationContext evaluationContext =
                    new MethodBasedEvaluationContext(
                            null, method, arguments, parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(expression)
                    .getValue(evaluationContext);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException exception) {
            log.warn(
                    "解析审计业务编号失败，方法={}，表达式={}",
                    method.toGenericString(),
                    expression,
                    exception);
            return null;
        }
    }

    /**
     * 按注解约束序列化并截断请求参数。
     *
     * @param enabled 是否允许记录请求参数
     * @param maxLength 允许记录的最大字符数
     * @param method 当前业务方法
     * @param arguments 当前方法参数
     * @return 参数 JSON；关闭、长度无效或序列化失败时返回 {@code null}
     */
    private String serializeArguments(
            boolean enabled,
            int maxLength,
            Method method,
            Object[] arguments) {
        if (!enabled || maxLength <= 0) {
            return null;
        }
        try {
            String json = jsonCodec.write(arguments);
            if (json == null || json.length() <= maxLength) {
                return json;
            }
            return json.substring(0, maxLength);
        } catch (RuntimeException exception) {
            log.warn("序列化审计请求参数失败，方法={}", method.toGenericString(), exception);
            return null;
        }
    }

    /**
     * 提取并限制业务异常消息长度。
     *
     * @param failure 业务方法异常，成功时为 {@code null}
     * @return 可写入审计事件的异常摘要
     */
    private String resolveErrorMessage(Throwable failure) {
        if (failure == null) {
            return null;
        }
        String message = failure.getMessage();
        String resolved = message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
        return resolved.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? resolved
                : resolved.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    /**
     * 输出审计事件，并隔离输出端异常。
     *
     * @param event 已完成组装的审计事件
     * @param method 当前业务方法
     */
    private void recordSafely(AuditLogEvent event, Method method) {
        try {
            auditLogService.record(event);
        } catch (RuntimeException exception) {
            log.error("记录审计日志失败，方法={}", method.toGenericString(), exception);
        }
    }
}
