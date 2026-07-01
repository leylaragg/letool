package com.github.leyland.letool.rule.store;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileRuleStore 测试")
class FileRuleStoreTest {

    private FileRuleStore store;

    @BeforeEach
    void setUp() {
        store = new FileRuleStore();
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应使用默认 classpath 路径")
        void shouldUseDefaultPath() {
            FileRuleStore s = new FileRuleStore();
            assertNotNull(s);
        }

        @Test
        @DisplayName("自定义路径构造应工作正常")
        void shouldAcceptCustomPath() {
            FileRuleStore s = new FileRuleStore("/custom/path/");
            assertNotNull(s);
        }
    }

    @Nested
    @DisplayName("save 方法")
    class SaveTests {

        @Test
        @DisplayName("classpath 模式下 save 应抛出 UnsupportedOperationException")
        void shouldThrowOnClasspathSave() {
            FileRuleStore classpathStore = new FileRuleStore("classpath:rule/");
            ChainDefinition chain = new ChainDefinition("test");

            assertThrows(UnsupportedOperationException.class, () -> classpathStore.save(chain));
        }

        @Test
        @DisplayName("null 规则链应抛出 IllegalArgumentException")
        void shouldThrowOnNullChain() {
            FileRuleStore fsStore = new FileRuleStore("/tmp/test-rules/");
            assertThrows(IllegalArgumentException.class, () -> fsStore.save(null));
        }

        @Test
        @DisplayName("无名称的规则链应抛出 IllegalArgumentException")
        void shouldThrowOnNamelessChain() {
            FileRuleStore fsStore = new FileRuleStore("/tmp/test-rules/");
            ChainDefinition chain = new ChainDefinition();

            assertThrows(IllegalArgumentException.class, () -> fsStore.save(chain));
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTests {

        @Test
        @DisplayName("classpath 模式下 delete 应抛出 UnsupportedOperationException")
        void shouldThrowOnClasspathDelete() {
            FileRuleStore classpathStore = new FileRuleStore("classpath:rule/");
            assertThrows(UnsupportedOperationException.class, () -> classpathStore.delete("testChain"));
        }
    }

    @Nested
    @DisplayName("load 方法")
    class LoadTests {

        @Test
        @DisplayName("不存在的规则链应返回 null")
        void shouldReturnNullForMissingChain() {
            FileRuleStore classpathStore = new FileRuleStore("classpath:rule/");
            assertNull(classpathStore.load("nonexistentChain"));
        }
    }

    @Nested
    @DisplayName("listAll 方法")
    class ListAllTests {

        @Test
        @DisplayName("classpath 下无规则目录应返回空列表")
        void shouldReturnEmptyOnMissingClasspath() {
            FileRuleStore classpathStore = new FileRuleStore("classpath:rule/nonexistent/");
            List<ChainDefinition> chains = classpathStore.listAll();
            assertNotNull(chains);
            assertTrue(chains.isEmpty());
        }

        @Test
        @DisplayName("不存在的文件系统目录应返回空列表")
        void shouldReturnEmptyOnMissingFileSystemDir() {
            FileRuleStore fsStore = new FileRuleStore("/nonexistent/path/");
            List<ChainDefinition> chains = fsStore.listAll();
            assertNotNull(chains);
            assertTrue(chains.isEmpty());
        }
    }
}
