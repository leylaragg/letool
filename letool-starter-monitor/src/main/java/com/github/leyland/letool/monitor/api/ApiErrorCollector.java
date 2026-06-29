package com.github.leyland.letool.monitor.api;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 异常/错误采集器.
 *
 * <p>用于记录 API 请求过程中的异常信息，按异常类型和 API 路径进行统计。
 * 支持查询全局错误分布和指定路径的错误详情。</p>
 *
 * <p>所有方法均线程安全，基于 {@link ConcurrentHashMap} 和 {@link AtomicLong}。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ApiErrorCollector {

    // ======================== 字段 ========================

    /** 异常类型 -> 发生次数 */
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

    /** API 路径 -> 关联错误列表 */
    private final ConcurrentHashMap<String, List<ErrorInfo>> errorsByPath = new ConcurrentHashMap<>();

    // ======================== 公共方法 ========================

    /**
     * 记录一次 API 错误.
     *
     * @param exceptionClass 异常类全限定名
     * @param path           API 请求路径
     * @param message        错误消息（截取前 500 字符）
     */
    public void recordError(String exceptionClass, String path, String message) {
        // 全局错误计数
        errorCounts.computeIfAbsent(exceptionClass, k -> new AtomicLong(0)).incrementAndGet();

        // 按路径记录错误详情（截取消息防止过长）
        String truncatedMessage = message != null && message.length() > 500
                ? message.substring(0, 500) + "..."
                : message;

        ErrorInfo errorInfo = new ErrorInfo(
                exceptionClass,
                truncatedMessage,
                path,
                1,
                LocalDateTime.now()
        );

        errorsByPath.compute(path, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            // 尝试合并相同异常类型的记录
            for (ErrorInfo existing : list) {
                if (existing.getExceptionClass().equals(exceptionClass)
                        && Objects.equals(existing.getPath(), path)) {
                    existing.increment();
                    return list;
                }
            }
            list.add(errorInfo);
            return list;
        });
    }

    /**
     * 获取错误的全局细分统计.
     *
     * @return 异常类型全限定名 -> 发生次数的不可变 Map
     */
    public Map<String, Long> getErrorBreakdown() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : errorCounts.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取指定 API 路径的所有错误记录.
     *
     * @param path API 请求路径
     * @return 该路径的错误信息列表，无记录时返回空列表
     */
    public List<ErrorInfo> getErrorsByPath(String path) {
        List<ErrorInfo> list = errorsByPath.get(path);
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableList(list);
    }

    /**
     * 获取所有有过错误记录的 API 路径.
     *
     * @return 路径集合
     */
    public Set<String> getErrorPaths() {
        return Collections.unmodifiableSet(errorsByPath.keySet());
    }

    /**
     * 获取某个异常类型的累计发生次数.
     *
     * @param exceptionClass 异常类全限定名
     * @return 发生次数，未记录过则返回 0
     */
    public long getErrorCount(String exceptionClass) {
        AtomicLong count = errorCounts.get(exceptionClass);
        return count != null ? count.get() : 0;
    }

    /**
     * 重置所有错误记录.
     */
    public void reset() {
        errorCounts.clear();
        errorsByPath.clear();
    }

    // ======================== 内部类：ErrorInfo ========================

    /**
     * API 错误详细信息.
     *
     * <p>包含异常类型、错误消息、触发路径、发生次数和最近一次发生时间。</p>
     */
    public static class ErrorInfo {

        /** 异常类全限定名 */
        private final String exceptionClass;

        /** 错误消息（已截取） */
        private final String message;

        /** 触发错误的 API 路径 */
        private final String path;

        /** 发生次数 */
        private long count;

        /** 最近一次发生时间 */
        private LocalDateTime lastOccurrence;

        /**
         * 创建错误详情.
         *
         * @param exceptionClass 异常类名
         * @param message        错误消息
         * @param path           API 路径
         * @param count          发生次数
         * @param lastOccurrence 最近发生时间
         */
        public ErrorInfo(String exceptionClass, String message, String path,
                         long count, LocalDateTime lastOccurrence) {
            this.exceptionClass = exceptionClass;
            this.message = message;
            this.path = path;
            this.count = count;
            this.lastOccurrence = lastOccurrence;
        }

        /** 次数递增 1，同时更新最近发生时间为当前时间. */
        public void increment() {
            this.count++;
            this.lastOccurrence = LocalDateTime.now();
        }

        // ---- Getter ----

        /** @return 异常类全限定名 */
        public String getExceptionClass() { return exceptionClass; }
        /** @return 错误消息 */
        public String getMessage() { return message; }
        /** @return 触发错误的 API 路径 */
        public String getPath() { return path; }
        /** @return 发生次数 */
        public long getCount() { return count; }
        /** @return 最近一次发生时间 */
        public LocalDateTime getLastOccurrence() { return lastOccurrence; }

        @Override
        public String toString() {
            return "ErrorInfo{" +
                    "exception='" + exceptionClass + '\'' +
                    ", path='" + path + '\'' +
                    ", count=" + count +
                    ", lastOccurrence=" + lastOccurrence +
                    '}';
        }
    }
}
