package com.github.leyland.letool.log.level;

import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态日志级别修改器 —— 运行时不重启服务即可修改/恢复 Logback 日志级别.
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>生产问题排查：临时提升某个包为 DEBUG 级别，排查完成后恢复</li>
 *   <li>性能调优：临时关闭某些嘈杂的 INFO 日志</li>
 * </ul>
 *
 * <h3>工作流程</h3>
 * <pre>
 *   setLevel("com.example", "DEBUG")
 *     → 保存原始级别到 ORIGINAL_LEVELS
 *     → 设置新级别
 *
 *   restore("com.example")
 *     → 从 ORIGINAL_LEVELS 取出原始级别
 *     → 恢复
 * </pre>
 *
 * <p>目前仅支持 Logback。如需支持 Log4j2，需额外实现。
 */
public class DynamicLogLevelChanger {

    private static final Logger log = LoggerFactory.getLogger(DynamicLogLevelChanger.class);

    // 保存原始级别，用于 restore 时恢复（ConcurrentHashMap 保证并发安全）
    private static final Map<String, Level> ORIGINAL_LEVELS = new ConcurrentHashMap<>();

    /**
     * 设置指定 Logger 的日志级别.
     *
     * @param loggerName Logger 名称（通常为包名或类全限定名）
     * @param level      目标级别：TRACE / DEBUG / INFO / WARN / ERROR
     */
    public static void setLevel(String loggerName, String level) {
        ch.qos.logback.classic.Logger logger = getLogger(loggerName);
        if (logger != null) {
            // ==== 首次修改时保存原始级别，供后续 restore ====
            ORIGINAL_LEVELS.computeIfAbsent(loggerName, k -> logger.getLevel());
            logger.setLevel(Level.toLevel(level));
            log.info("日志级别已修改: {} → {}", loggerName, level);
        }
    }

    /**
     * 恢复指定 Logger 的原始日志级别.
     */
    public static void restore(String loggerName) {
        Level original = ORIGINAL_LEVELS.remove(loggerName);
        if (original != null) {
            ch.qos.logback.classic.Logger logger = getLogger(loggerName);
            if (logger != null) {
                logger.setLevel(original);
                log.info("日志级别已恢复: {} → {}", loggerName, original);
            }
        }
    }

    /**
     * 获取当前日志级别.
     *
     * @return 当前级别字符串，未设置则返回 "UNKNOWN"
     */
    public static String getLevel(String loggerName) {
        ch.qos.logback.classic.Logger logger = getLogger(loggerName);
        return logger != null && logger.getLevel() != null ? logger.getLevel().toString() : "UNKNOWN";
    }

    /**
     * 获取 Logback 原生 Logger —— 通过 SLF4J 桥接获取底层实现.
     */
    private static ch.qos.logback.classic.Logger getLogger(String name) {
        org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(name);
        if (slf4jLogger instanceof ch.qos.logback.classic.Logger) {
            return (ch.qos.logback.classic.Logger) slf4jLogger;
        }
        // Log4j2 或其他实现 → 不支持
        return null;
    }
}
