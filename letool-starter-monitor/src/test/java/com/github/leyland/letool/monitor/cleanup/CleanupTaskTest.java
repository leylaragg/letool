package com.github.leyland.letool.monitor.cleanup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CleanupTask 数据清理任务测试")
class CleanupTaskTest {

    @Nested
    @DisplayName("构造方法和 getter")
    class ConstructorTests {

        @Test
        @DisplayName("构造函数设置表名和保留天数")
        void constructor() {
            CleanupTask task = new CleanupTask("letool_audit_log", 90);

            assertEquals("letool_audit_log", task.getTableName());
            assertEquals(90, task.getRetentionDays());
            assertEquals(0, task.getLastCleanupTime());
        }

        @Test
        @DisplayName("初始 lastCleanupTime 为 0")
        void initialLastCleanupTime() {
            CleanupTask task = new CleanupTask("test_table", 30);
            assertEquals(0, task.getLastCleanupTime());
        }

        @Test
        @DisplayName("初始 getLastCleanupDateTime 返回 null")
        void initialLastCleanupDateTime() {
            CleanupTask task = new CleanupTask("test_table", 30);
            assertNull(task.getLastCleanupDateTime());
        }
    }

    @Nested
    @DisplayName("shouldCleanup 判断")
    class ShouldCleanupTests {

        @Test
        @DisplayName("初始状态返回 true（从未清理过）")
        void shouldCleanupInitial() {
            CleanupTask task = new CleanupTask("test_table", 30);
            assertTrue(task.shouldCleanup());
        }

        @Test
        @DisplayName("刚执行完成后返回 false")
        void shouldCleanupAfterExecute() {
            CleanupTask task = new CleanupTask("test_table", 30);
            task.execute();
            assertFalse(task.shouldCleanup());
        }
    }

    @Nested
    @DisplayName("execute 执行")
    class ExecuteTests {

        @Test
        @DisplayName("execute 返回 0（占位实现）")
        void executeReturnsZero() {
            CleanupTask task = new CleanupTask("test_table", 30);
            assertEquals(0, task.execute());
        }

        @Test
        @DisplayName("execute 更新 lastCleanupTime")
        void executeUpdatesLastCleanupTime() {
            CleanupTask task = new CleanupTask("test_table", 30);
            task.execute();
            assertTrue(task.getLastCleanupTime() > 0);
        }

        @Test
        @DisplayName("execute 后 getLastCleanupDateTime 不为 null")
        void executeSetsLastCleanupDateTime() {
            CleanupTask task = new CleanupTask("test_table", 30);
            task.execute();
            assertNotNull(task.getLastCleanupDateTime());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("未执行时显示 never")
        void toStringNeverExecuted() {
            CleanupTask task = new CleanupTask("my_table", 60);
            String str = task.toString();
            assertTrue(str.contains("my_table"));
            assertTrue(str.contains("60"));
            assertTrue(str.contains("never"));
        }

        @Test
        @DisplayName("执行后显示时间")
        void toStringAfterExecute() {
            CleanupTask task = new CleanupTask("my_table", 60);
            task.execute();
            String str = task.toString();
            assertTrue(str.contains("my_table"));
            assertTrue(str.contains("60"));
            assertFalse(str.contains("never"));
        }
    }
}
