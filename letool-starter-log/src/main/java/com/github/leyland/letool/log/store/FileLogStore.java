package com.github.leyland.letool.log.store;

import com.github.leyland.letool.tool.util.JsonUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 文件持久化存储 —— JSON Lines 格式（{@code .jsonl}），按日期自动滚动.
 *
 * <h3>文件命名</h3>
 * <pre>{@code
 * {baseDir}/log-2026-06-25.jsonl
 * {baseDir}/log-2026-06-26.jsonl
 * }</pre>
 *
 * <p>跨天自动切换到新文件，写入使用 {@link ReentrantLock} 保证线程安全.
 * 每条记录序列化为一行 JSON，末尾追加 {@code \n} 换行符.</p>
 */
public class FileLogStore<T> implements LogRecordStore<T> {

    /** 文件名日期格式 —— yyyy-MM-dd */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日志文件存放根目录 */
    private final Path baseDir;

    /** 记录类型 —— 用于反序列化 queryRecent() 返回的 JSON 行 */
    private final Class<T> type;

    /** 写锁 —— 保证跨线程写入安全，防止多线程同时往同一文件追加导致行交错 */
    private final ReentrantLock writeLock = new ReentrantLock();

    /** 当前活跃文件 —— volatile 保证跨线程可见，跨天时自动切换到新文件 */
    private volatile Path currentFile;

    public FileLogStore(String baseDir, Class<T> type) {
        this.baseDir = Paths.get(baseDir);
        this.type = type;
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建日志目录: " + baseDir, e);
        }
    }

    @Override
    public void save(T record) {
        writeLock.lock();
        try {
            // 获取当天对应的文件路径（跨天自动切换）
            Path file = getCurrentFile();
            // 序列化为 JSON + 换行符，追加写入文件
            String line = JsonUtil.toJsonString(record) + "\n";
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("日志写入失败", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 获取当前日期的文件路径 —— 以当天日期为文件名，跨天时自动指向新文件.
     */
    private Path getCurrentFile() {
        String today = LocalDate.now().format(DATE_FMT);
        Path file = baseDir.resolve("log-" + today + ".jsonl");
        // 首次调用或跨天时更新 currentFile 引用
        if (currentFile == null || !Files.exists(file) || !file.equals(currentFile)) {
            currentFile = file;
        }
        return currentFile;
    }

    @Override
    public List<T> queryRecent(int limit) {
        Path file = currentFile;
        if (file == null || !Files.exists(file)) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        // 流式读取 JSON Lines 文件，逐行反序列化
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                try {
                    result.add(JsonUtil.parseObject(line, type));
                } catch (Exception ignored) {
                    // 跳过损坏的行（反序列化失败不中断整体读取）
                }
            });
        } catch (IOException e) {
            return Collections.emptyList();
        }
        // 倒序排列 → 最新的在前
        Collections.reverse(result);
        if (limit > 0 && limit < result.size()) {
            return result.subList(0, limit);
        }
        return result;
    }

    @Override
    public long count() {
        Path file = currentFile;
        if (file == null || !Files.exists(file)) return 0;
        // 流式统计行数（O(n) 复杂度，大文件有开销）
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
