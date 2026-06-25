package com.github.leyland.letool.log.store;

import com.github.leyland.letool.log.audit.AuditLogEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryLogStoreTest {

    @Test
    void save_shouldAddRecord() {
        MemoryLogStore<AuditLogEvent> store = new MemoryLogStore<>(100);
        AuditLogEvent event = AuditLogEvent.builder().operation("test").build();
        store.save(event);
        assertEquals(1, store.count());
    }

    @Test
    void save_shouldEvictOldestWhenFull() {
        // 容量为 3 的环形缓冲区
        MemoryLogStore<AuditLogEvent> store = new MemoryLogStore<>(3);

        store.save(event("op1"));
        store.save(event("op2"));
        store.save(event("op3"));
        // 第 4 条触发淘汰
        store.save(event("op4"));

        assertEquals(3, store.count());
        List<AuditLogEvent> recent = store.queryRecent(10);
        // 最旧的是 op2, 最新的是 op4（倒序返回）
        assertEquals(3, recent.size());
        assertEquals("op4", recent.get(0).getOperation());
        assertEquals("op2", recent.get(2).getOperation());
    }

    @Test
    void queryRecent_shouldReturnReverseOrder() {
        MemoryLogStore<AuditLogEvent> store = new MemoryLogStore<>(10);
        store.save(event("op1"));
        store.save(event("op2"));
        store.save(event("op3"));

        List<AuditLogEvent> recent = store.queryRecent(10);
        assertEquals(3, recent.size());
        // 倒序：最近的在最前
        assertEquals("op3", recent.get(0).getOperation());
        assertEquals("op2", recent.get(1).getOperation());
        assertEquals("op1", recent.get(2).getOperation());
    }

    @Test
    void queryRecent_shouldRespectLimit() {
        MemoryLogStore<AuditLogEvent> store = new MemoryLogStore<>(10);
        for (int i = 0; i < 5; i++) {
            store.save(event("op" + i));
        }

        List<AuditLogEvent> recent = store.queryRecent(2);
        assertEquals(2, recent.size());
    }

    @Test
    void count_shouldReturnZeroForEmptyStore() {
        MemoryLogStore<AuditLogEvent> store = new MemoryLogStore<>(10);
        assertEquals(0, store.count());
    }

    private AuditLogEvent event(String operation) {
        return AuditLogEvent.builder().operation(operation).build();
    }
}
