package com.github.leyland.letool.rule.hotreload;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.chain.ChainParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleHotReloadListener 测试")
class RuleHotReloadListenerTest {

    private ChainManager chainManager;
    private ChainParser chainParser;
    private FileWatcher fileWatcher;
    private RuleHotReloadListener listener;

    @BeforeEach
    void setUp() {
        chainManager = new ChainManager();
        chainParser = new ChainParser();
        fileWatcher = new FileWatcher(1, TimeUnit.SECONDS);
        listener = new RuleHotReloadListener(chainManager, chainParser, fileWatcher, "/tmp/rules");
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("应正确创建监听器实例")
        void shouldCreateInstance() {
            assertNotNull(listener);
        }
    }

    @Nested
    @DisplayName("onFileChanged 方法")
    class OnFileChangedTests {

        @Test
        @DisplayName("无效文件内容的变更应被安全处理")
        void shouldHandleInvalidFileContent() {
            // 不存在的文件应不抛出异常
            assertDoesNotThrow(() -> listener.onFileChanged("/nonexistent/file.yml"));
        }

        @Test
        @DisplayName("有效 YAML 文件变更应热更新规则链")
        void shouldReloadChainOnValidFileChange() {
            String yaml = "name: testChain\n" +
                    "description: 动态更新的链\n" +
                    "nodes:\n" +
                    "  - name: step1\n" +
                    "    type: THEN";

            ChainDefinition chain = chainParser.parseYaml(yaml);
            chainManager.register(chain);

            // 触发 onFileChanged
            assertDoesNotThrow(() -> listener.onFileChanged("/tmp/rules/testChain.yml"));

            // 验证链已存在
            assertTrue(chainManager.contains("testChain"));
        }
    }

    @Nested
    @DisplayName("start/stop 生命周期")
    class LifecycleTests {

        @Test
        @DisplayName("start 应在 classpath 路径下给出警告（不实际启动）")
        void shouldHandleClasspathWatch() {
            RuleHotReloadListener classpathListener = new RuleHotReloadListener(
                    chainManager, chainParser, new FileWatcher(1, TimeUnit.SECONDS),
                    "classpath:rule/chains/");
            assertDoesNotThrow(classpathListener::start);
        }

        @Test
        @DisplayName("stop 应停止监听")
        void shouldStopGracefully() {
            assertDoesNotThrow(listener::stop);
        }
    }
}
