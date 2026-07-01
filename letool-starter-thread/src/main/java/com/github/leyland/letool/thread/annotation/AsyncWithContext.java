package com.github.leyland.letool.thread.annotation;

import java.lang.annotation.*;

/**
 * 带上下文传播的异步方法执行注解，用法类似 Spring 的 {@code @Async}。
 *
 * <p>标记此注解的方法将在指定线程池中异步执行，同时自动传递当前线程的
 * MDC（日志上下文）和安全上下文（SecurityContext）到子线程。返回类型
 * 应为 {@link java.util.concurrent.Future Future}、
 * {@link java.util.concurrent.CompletableFuture CompletableFuture} 或 {@code void}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @AsyncWithContext("orderThreadPool")
 * public CompletableFuture<Order> processAsync(Long orderId) {
 *     // 此方法在 orderThreadPool 中执行，自动继承父线程的 TraceId 和用户信息
 * }
 * }</pre>
 *
 * <p>上下文传播依赖 Spring 的 {@code AnnotationAsyncExecutionInterceptor}
 * 和 {@link com.github.leyland.letool.thread.propagation.MdcTaskDecorator MdcTaskDecorator}
 * 配合工作。线程池名称需与配置中 {@code letool.thread.pools.<name>} 匹配。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncWithContext {

    /** 目标线程池名称，默认 {@code "task-executor"} */
    String value() default "task-executor";
}
