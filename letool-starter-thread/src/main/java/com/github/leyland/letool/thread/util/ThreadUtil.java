package com.github.leyland.letool.thread.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 线程工具类，提供线程休眠、异步任务提交和虚拟线程检测等便捷方法。
 *
 * <p>所有方法均为静态方法，工具类不可实例化。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class ThreadUtil {

    /** 工具类，禁止实例化 */
    private ThreadUtil() {}

    /**
     * 使当前线程休眠指定毫秒数。
     *
     * <p>被中断时自动恢复中断状态并立即返回，不抛出异常。</p>
     *
     * @param millis 休眠毫秒数
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 使当前线程休眠指定时长。
     *
     * @param duration 休眠时长
     * @param unit     时间单位
     */
    public static void sleep(long duration, TimeUnit unit) {
        try {
            unit.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 异步执行无返回值的任务。
     *
     * @param task     要执行的任务
     * @param executor 线程池
     * @return CompletableFuture 实例，可用于等待完成或链式编排
     */
    public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
        return CompletableFuture.runAsync(task, executor);
    }

    /**
     * 异步执行有返回值的任务。
     *
     * @param supplier 返回值提供者
     * @param executor 线程池
     * @param <T>      返回值类型
     * @return CompletableFuture 实例，包含异步计算结果
     */
    public static <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * 检测当前 JDK 是否支持虚拟线程（Java 21+）。
     *
     * <p>通过反射检测 {@code java.lang.VirtualThread} 类是否存在，
     * 而非直接判断 JDK 版本号，确保在未来的 JDK 发行版中也能正确检测。</p>
     *
     * @return {@code true} 如果支持虚拟线程
     */
    public static boolean isVirtualThreadsSupported() {
        try {
            Class.forName("java.lang.VirtualThread");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
