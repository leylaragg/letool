package com.github.leyland.letool.rule.hotreload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 文件变更监听器 —— 通过轮询机制监听规则链文件的变化.
 *
 * <h3>监听策略</h3>
 * <p>使用 {@link ScheduledExecutorService} 定时检查文件最后修改时间（lastModified），
 * 相比 Java 原生 {@link WatchService} 更具兼容性（尤其在不同操作系统和网络驱动器上）.</p>
 *
 * <h3>监听生命周期</h3>
 * <ol>
 *   <li>{@link #watch(String, Consumer)} —— 注册监听目录和回调</li>
 *   <li>{@link #start()} —— 启动定时检查任务</li>
 *   <li>检测到文件变化时触发回调 {@code onChange}</li>
 *   <li>{@link #stop()} —— 停止监听</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * FileWatcher watcher = new FileWatcher(10, TimeUnit.SECONDS);
 * watcher.watch("classpath:rule/chains/", changedFile -> {
 *     log.info("规则链文件已变更: {}", changedFile);
 * });
 * watcher.start();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleHotReloadListener
 */
public class FileWatcher {

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);

    /** 检查间隔 */
    private final long checkInterval;

    /** 检查间隔的时间单位 */
    private final TimeUnit timeUnit;

    /** 定时任务执行器 */
    private ScheduledExecutorService scheduler;

    /** 文件最后修改时间记录 */
    private final Map<String, Long> lastModifiedMap;

    /** 文件变更回调 */
    private Consumer<String> onChangeCallback;

    /** 监听目录 */
    private String watchDirectory;

    /** 是否正在运行 */
    private volatile boolean running = false;

    // ======================== 构造方法 ========================

    /**
     * 创建文件变更监听器.
     *
     * @param checkInterval 检查间隔数值
     * @param timeUnit      检查间隔时间单位
     */
    public FileWatcher(long checkInterval, TimeUnit timeUnit) {
        this.checkInterval = checkInterval;
        this.timeUnit = timeUnit;
        this.lastModifiedMap = new ConcurrentHashMap<>();
    }

    /**
     * 创建文件变更监听器（默认 10 秒检查间隔）.
     */
    public FileWatcher() {
        this(10, TimeUnit.SECONDS);
    }

    // ======================== 监听设置 ========================

    /**
     * 注册监听目录和变更回调.
     *
     * @param directory 监听目录路径
     * @param onChange  文件变更时的回调函数，参数为变更的文件路径
     */
    public void watch(String directory, Consumer<String> onChange) {
        this.watchDirectory = directory;
        this.onChangeCallback = onChange;
        log.info("文件监听已注册，目录: {}", directory);
    }

    // ======================== 生命周期管理 ========================

    /**
     * 启动文件监听.
     *
     * <p>启动定时任务，按配置的间隔定期检查文件修改时间.</p>
     */
    public void start() {
        if (running) {
            log.warn("文件监听已在运行中");
            return;
        }
        if (watchDirectory == null) {
            log.warn("未设置监听目录，无法启动文件监听");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rule-file-watcher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::checkFiles, 0, checkInterval, timeUnit);
        log.info("文件监听已启动，检查间隔: {} {}", checkInterval, timeUnit.name().toLowerCase());
    }

    /**
     * 停止文件监听.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        lastModifiedMap.clear();
        log.info("文件监听已停止");
    }

    /**
     * 检查是否正在运行.
     *
     * @return true 表示正在运行
     */
    public boolean isRunning() {
        return running;
    }

    // ======================== 文件检查核心逻辑 ========================

    /**
     * 定时任务：检查监听目录下的文件变化.
     */
    private void checkFiles() {
        if (!running || watchDirectory == null) {
            return;
        }

        try {
            Path dirPath = resolveWatchDirectory();
            if (dirPath == null || !Files.isDirectory(dirPath)) {
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath,
                    "*.{yml,yaml,groovy}")) {
                for (Path file : stream) {
                    checkFileChange(file);
                }
            }
        } catch (IOException e) {
            log.error("检查文件变化时发生 I/O 错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查单个文件是否发生变化.
     *
     * @param filePath 文件路径
     */
    private void checkFileChange(Path filePath) {
        try {
            long currentModified = Files.getLastModifiedTime(filePath).toMillis();
            String absolutePath = filePath.toAbsolutePath().toString();

            Long lastModified = lastModifiedMap.get(absolutePath);

            if (lastModified == null) {
                // 新文件，记录但不触发回调
                lastModifiedMap.put(absolutePath, currentModified);
            } else if (currentModified > lastModified) {
                // 文件已更新
                lastModifiedMap.put(absolutePath, currentModified);
                log.info("检测到文件变更: {}", filePath.getFileName());

                if (onChangeCallback != null) {
                    try {
                        onChangeCallback.accept(absolutePath);
                    } catch (Exception e) {
                        log.error("文件变更回调执行失败: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("读取文件修改时间失败: {}", filePath, e.getMessage());
        }
    }

    // ======================== 路径解析 ========================

    /**
     * 解析监听目录路径.
     *
     * @return 文件系统路径，解析失败返回 null
     */
    private Path resolveWatchDirectory() {
        if (watchDirectory == null) {
            return null;
        }

        try {
            if (watchDirectory.startsWith("classpath:")) {
                // classpath 资源不支持文件系统监听，记录警告
                log.warn("classpath 路径不支持文件系统监听: {}。请配置为文件系统绝对路径以启用热重载.",
                        watchDirectory);
                return null;
            }
            return Paths.get(watchDirectory);
        } catch (Exception e) {
            log.error("解析监听路径失败: {}", watchDirectory, e);
            return null;
        }
    }
}
