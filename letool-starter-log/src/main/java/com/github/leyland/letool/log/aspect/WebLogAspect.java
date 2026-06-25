package com.github.leyland.letool.log.aspect;

import com.github.leyland.letool.log.config.LogProperties;
import com.github.leyland.letool.log.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 请求日志切面 —— 拦截所有 Controller 层方法，自动记录请求路径/方法/耗时/状态.
 *
 * <h3>执行流程</h3>
 * <pre>
 *   1. 检查 web-log.enabled → false 则透传
 *   2. 从 RequestContextHolder 获取 HttpServletRequest（需在 Servlet 容器中运行）
 *   3. 匹配排除路径 → 命中则跳过日志记录
 *   4. 执行目标方法 → 记录耗时
 *   5. 成功 → INFO 日志；异常 → ERROR 日志
 * </pre>
 */
@Aspect
public class WebLogAspect {

    private static final Logger log = LoggerFactory.getLogger(WebLogAspect.class);

    private final LogProperties properties;

    public WebLogAspect(LogProperties properties) {
        this.properties = properties;
    }

    /**
     * Around 通知 —— 拦截所有 @RestController 和 @Controller 注解的类.
     *
     * <p>使用 @within 切点表达式匹配类上的注解，而非方法级别.
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.stereotype.Controller)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        LogProperties.WebLog config = properties.getWebLog();
        // ==== 1. 全局开关检查 ====
        if (!config.isEnabled()) {
            return joinPoint.proceed();
        }

        // ==== 2. 获取当前请求上下文（非 Servlet 环境直接透传）====
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        String path = request.getRequestURI();

        // ==== 3. 排除路径匹配（支持 Ant 风格 ** 通配符）====
        for (String excludePath : config.getExcludePaths()) {
            // 将 Ant 风格 ** 转为正则 .*
            if (path.matches(excludePath.replace("**", ".*"))) {
                return joinPoint.proceed();
            }
        }

        // ==== 4. 获取/生成 TraceId ====
        String traceId = TraceContext.getOrGenerate();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("[{}] {} {} → 200 ({}ms)", traceId, request.getMethod(), path, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;
            // 异常时记录 ERROR 级别，异常继续向上抛给 GlobalExceptionHandler
            log.error("[{}] {} {} → 500 ({}ms): {}", traceId, request.getMethod(), path, duration, e.getMessage());
            throw e;
        }
    }
}
