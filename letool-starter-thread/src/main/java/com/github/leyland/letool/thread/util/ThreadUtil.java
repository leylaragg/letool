package com.github.leyland.letool.thread.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ThreadUtil {

    private ThreadUtil() {}

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleep(long duration, TimeUnit unit) {
        try {
            unit.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
        return CompletableFuture.runAsync(task, executor);
    }

    public static <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public static boolean isVirtualThreadsSupported() {
        try {
            Class.forName("java.lang.VirtualThread");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
