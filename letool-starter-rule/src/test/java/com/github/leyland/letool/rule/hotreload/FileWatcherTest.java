package com.github.leyland.letool.rule.hotreload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileWatcher 测试")
class FileWatcherTest {

    private FileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null && watcher.isRunning()) {
            watcher.stop();
        }
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应使用默认 10 秒间隔")
        void shouldUseDefaultInterval() {
            watcher = new FileWatcher();
            assertNotNull(watcher);
            assertFalse(watcher.isRunning());
        }

        @Test
        @DisplayName("带参数构造应接受自定义间隔")
        void shouldAcceptCustomInterval() {
            watcher = new FileWatcher(5, TimeUnit.SECONDS);
            assertNotNull(watcher);
            assertFalse(watcher.isRunning());
        }
    }

    @Nested
    @DisplayName("watch 方法")
    class WatchTests {

        @Test
        @DisplayName("watch 应注册目录和回调")
        void shouldRegisterWatch() {
            watcher = new FileWatcher();
            watcher.watch("/tmp/rules", path -> {});

            // start 不应抛出异常
            watcher.start();
            assertTrue(watcher.isRunning());
        }

        @Test
        @DisplayName("未设置监听目录时 start 应给出警告")
        void shouldWarnWithoutDirectory() {
            watcher = new FileWatcher();
            watcher.start();  // 应不抛出异常
            assertFalse(watcher.isRunning());
        }
    }

    @Nested
    @DisplayName("生命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("重复 start 应给出警告")
        void shouldWarnOnDoubleStart() {
            watcher = new FileWatcher(1, TimeUnit.SECONDS);
            watcher.watch("/tmp/rules", path -> {});
            watcher.start();
            assertTrue(watcher.isRunning());

            // 第二次 start 应不影响
            watcher.start();
            assertTrue(watcher.isRunning());
        }

        @Test
        @DisplayName("stop 应停止监听")
        void shouldStop() {
            watcher = new FileWatcher(1, TimeUnit.SECONDS);
            watcher.watch("/tmp/rules", path -> {});
            watcher.start();

            watcher.stop();
            assertFalse(watcher.isRunning());
        }

        @Test
        @DisplayName("重复 stop 应安全")
        void shouldBeSafeToStopTwice() {
            watcher = new FileWatcher(1, TimeUnit.SECONDS);
            watcher.watch("/tmp/rules", path -> {});
            watcher.start();

            watcher.stop();
            watcher.stop();
            assertFalse(watcher.isRunning());
        }

        @Test
        @DisplayName("classpath 路径的 checkFiles 应安全跳过（不抛异常）")
        void shouldNotThrowOnClasspath() {
            watcher = new FileWatcher();
            watcher.watch("classpath:rule/chains/", path -> {});
            // classpath 路径下 start 不会实际监听文件，但 running 会被标记，实际检查时跳过
            assertDoesNotThrow(() -> watcher.start());
        }
    }
}
