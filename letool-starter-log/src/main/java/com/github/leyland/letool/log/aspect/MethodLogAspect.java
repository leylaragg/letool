package com.github.leyland.letool.log.aspect;

import com.github.leyland.letool.log.annotation.MethodLog;
import com.github.leyland.letool.log.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 方法日志切面 —— 拦截 @MethodLog 注解标记的方法，自动记录入参/出参/耗时/异常.
 *
 * <h3>执行流程</h3>
 * <pre>
 *   1. 解析注解 → 获取日志标题、是否记录入参/出参/异常
 *   2. 生成/获取 TraceId → 写入 MDC
 *   3. 记录入参（可选）→ 序列化 args 数组（敏感字段需提前脱敏）
 *   4. 执行目标方法 → 计时
 *   5. 记录出参（可选）→ 截断至 maxResultLength
 *   6. 捕获异常 → 记录异常信息（不吞异常，继续向上抛）
 * </pre>
 */
@Aspect
public class MethodLogAspect {

    /**
     * Around 通知 —— 环绕拦截所有标注 @MethodLog 的方法.
     *
     * <p>使用 @annotation 切点表达式精确匹配，不拦截父类/接口方法上的注解（JDK 默认行为）.
     */
    @Around("@annotation(com.github.leyland.letool.log.annotation.MethodLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 从 AOP 元数据中提取目标方法和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MethodLog annotation = method.getAnnotation(MethodLog.class);

        // 获取目标类专属 Logger，确保日志输出到正确的类名下
        Logger log = LoggerFactory.getLogger(method.getDeclaringClass());

        // 标题：优先用注解 value，未设置则用方法名
        String title = annotation.value().isEmpty() ? method.getName() : annotation.value();

        // 确保当前线程有 TraceId —— 如果是 Web 请求，TraceIdFilter 已经注入
        String traceId = TraceContext.getOrGenerate();

        // ==== 1. 记录入参 ====
        if (annotation.logArgs()) {
            // 注意：args 可能包含敏感数据（密码/手机号），建议在调用方手动脱敏后再入参
            log.info("[{}] {}.{} 入参: {}", traceId, method.getDeclaringClass().getSimpleName(),
                    method.getName(), joinPoint.getArgs());
        }

        long start = System.currentTimeMillis();
        try {
            // ==== 2. 执行目标方法 ====
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            // ==== 3. 记录出参（仅在成功时）====
            if (annotation.logResult()) {
                String resultStr = formatResult(result, annotation.maxResultLength());
                log.info("[{}] {}.{} 出参: {} (耗时: {}ms)", traceId,
                        method.getDeclaringClass().getSimpleName(), method.getName(), resultStr, duration);
            }
            return result;
        } catch (Throwable e) {
            // ==== 4. 记录异常（不吞异常，继续向上抛给调用方）====
            long duration = System.currentTimeMillis() - start;
            if (annotation.logException()) {
                log.error("[{}] {}.{} 异常 (耗时: {}ms): {}", traceId,
                        method.getDeclaringClass().getSimpleName(), method.getName(), duration, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 格式化返回值 —— null 安全 + 超长截断.
     * <p>对于超大对象或集合，避免日志输出爆炸（例如查询 10 万条数据）.
     */
    private String formatResult(Object result, int maxLength) {
        if (result == null) return "null";
        String str = result.toString();
        if (str.length() > maxLength) {
            return str.substring(0, maxLength) + "...";
        }
        return str;
    }
}
