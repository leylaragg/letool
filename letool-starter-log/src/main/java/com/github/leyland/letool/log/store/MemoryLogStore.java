package com.github.leyland.letool.log.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 内存环形缓冲区存储 —— 固定容量，先进先出，重启丢失.
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用 ConcurrentLinkedDeque 保证线程安全，无锁读写</li>
 *   <li>容量满时自动丢弃最旧条目（pollFirst）</li>
 *   <li>queryRecent 返回倒序列表（最新在前）</li>
 * </ul>
 */
public class MemoryLogStore<T> implements LogRecordStore<T> {

    private final int maxEntries;
    private final ConcurrentLinkedDeque<T> buffer;

    public MemoryLogStore(int maxEntries) {
        this.maxEntries = maxEntries;
        // ConcurrentLinkedDeque：无界双端队列，CAS 操作保证线程安全
        this.buffer = new ConcurrentLinkedDeque<>();
    }

    @Override
    public void save(T record) {
        // 新记录追加到尾部
        buffer.addLast(record);
        // 超过容量时从头部移除最旧记录（环形缓冲区语义）
        while (buffer.size() > maxEntries) {
            buffer.pollFirst();
        }
    }

    @Override
    public List<T> queryRecent(int limit) {
        // 拍快照 → 反转 → 截取前 limit 条
        List<T> all = new ArrayList<>(buffer);
        Collections.reverse(all);
        if (limit > 0 && limit < all.size()) {
            return all.subList(0, limit);
        }
        return all;
    }

    @Override
    public long count() {
        return buffer.size();
    }
}
