package io.github.leylaragg.letool.thread.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Async;

import java.lang.annotation.*;

/**
 * 带上下文传播的异步方法执行注解，用法类似 Spring 的 {@code @Async}。
 *
 * <p>该注解是 Spring {@link Async} 的组合注解。标记的方法将在指定线程池中
 * 异步执行；当目标执行器配置了任务装饰器时，可传递 MDC 日志上下文。返回类型
 * 应为 {@link java.util.concurrent.Future Future}、
 * {@link java.util.concurrent.CompletableFuture CompletableFuture} 或 {@code void}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @AsyncWithContext("letoolIoExecutor")
 * public CompletableFuture<Order> processAsync(Long orderId) {
 *     // 此方法在 letoolIoExecutor 中执行，并继承默认装饰器传播的 MDC
 * }
 * }</pre>
 *
 * <p>执行器名称必须匹配 Spring 容器中的 {@link java.util.concurrent.Executor Executor}
 * 或 {@link org.springframework.core.task.TaskExecutor TaskExecutor} Bean。模块默认提供
 * {@code letoolTaskExecutor} 和 {@code letoolIoExecutor} 两个执行器。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Async
public @interface AsyncWithContext {

    /**
     * 目标执行器 Bean 名称。
     *
     * @return Spring 异步执行器的 Bean 名称
     */
    @AliasFor(annotation = Async.class, attribute = "value")
    String value() default "letoolTaskExecutor";
}
