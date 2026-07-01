package com.github.leyland.letool.rule.chain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChainManager 测试")
class ChainManagerTest {

    private ChainManager manager;

    @BeforeEach
    void setUp() {
        manager = new ChainManager();
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应创建空注册表")
        void shouldCreateEmptyRegistry() {
            assertEquals(0, manager.size());
        }

        @Test
        @DisplayName("带 ChainParser 的构造应正常工作")
        void shouldAcceptCustomParser() {
            ChainParser parser = new ChainParser();
            ChainManager mgr = new ChainManager(parser);
            assertNotNull(mgr);
        }
    }

    @Nested
    @DisplayName("register 注册方法")
    class RegisterTests {

        @Test
        @DisplayName("register 应成功注册规则链")
        void shouldRegisterChain() {
            ChainDefinition chain = new ChainDefinition("testChain");
            manager.register(chain);

            assertEquals(1, manager.size());
            assertTrue(manager.contains("testChain"));
        }

        @Test
        @DisplayName("register null 应抛出 IllegalArgumentException")
        void shouldThrowOnNullChain() {
            assertThrows(IllegalArgumentException.class, () -> manager.register(null));
        }

        @Test
        @DisplayName("register 空名称应抛出 IllegalArgumentException")
        void shouldThrowOnEmptyName() {
            ChainDefinition chain = new ChainDefinition();
            assertThrows(IllegalArgumentException.class, () -> manager.register(chain));
        }

        @Test
        @DisplayName("register 同名链应覆盖旧链")
        void shouldOverrideDuplicateChain() {
            ChainDefinition chain1 = new ChainDefinition("dupChain");
            chain1.setDescription("v1");
            manager.register(chain1);

            ChainDefinition chain2 = new ChainDefinition("dupChain");
            chain2.setDescription("v2");
            manager.register(chain2);

            assertEquals(1, manager.size());
            assertEquals("v2", manager.get("dupChain").getDescription());
        }
    }

    @Nested
    @DisplayName("unregister 注销方法")
    class UnregisterTests {

        @Test
        @DisplayName("unregister 应移除并返回已注册的链")
        void shouldUnregisterChain() {
            ChainDefinition chain = new ChainDefinition("testChain");
            manager.register(chain);

            ChainDefinition removed = manager.unregister("testChain");

            assertSame(chain, removed);
            assertEquals(0, manager.size());
            assertFalse(manager.contains("testChain"));
        }

        @Test
        @DisplayName("unregister 不存在的链应返回 null")
        void shouldReturnNullForMissingChain() {
            assertNull(manager.unregister("nonexistent"));
        }
    }

    @Nested
    @DisplayName("get 查询方法")
    class GetTests {

        @Test
        @DisplayName("get 应返回已注册的链")
        void shouldGetRegisteredChain() {
            ChainDefinition chain = new ChainDefinition("myChain");
            manager.register(chain);

            assertSame(chain, manager.get("myChain"));
        }

        @Test
        @DisplayName("get 不存在的链应返回 null")
        void shouldReturnNullForMissingChain() {
            assertNull(manager.get("nonexistent"));
        }
    }

    @Nested
    @DisplayName("listAll 列表方法")
    class ListAllTests {

        @Test
        @DisplayName("listAll 应返回所有已注册的链")
        void shouldListAllChains() {
            manager.register(new ChainDefinition("chain1"));
            manager.register(new ChainDefinition("chain2"));
            manager.register(new ChainDefinition("chain3"));

            List<ChainDefinition> all = manager.listAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("空注册表应返回空列表")
        void shouldReturnEmptyList() {
            List<ChainDefinition> all = manager.listAll();
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("返回的列表应为只读")
        void shouldReturnUnmodifiableList() {
            manager.register(new ChainDefinition("chain1"));
            List<ChainDefinition> all = manager.listAll();

            assertThrows(UnsupportedOperationException.class,
                    () -> all.add(new ChainDefinition("chain2")));
        }
    }

    @Nested
    @DisplayName("reload 热更新方法")
    class ReloadTests {

        @Test
        @DisplayName("reload 应更新已存在的规则链")
        void shouldReloadExistingChain() {
            ChainDefinition old = new ChainDefinition("dynamicChain");
            old.setDescription("old");
            manager.register(old);

            ChainDefinition updated = new ChainDefinition("newName");
            updated.setDescription("new");
            ChainDefinition result = manager.reload("dynamicChain", updated);

            assertEquals("old", result.getDescription());
            assertEquals("dynamicChain", manager.get("dynamicChain").getName());
            assertEquals("new", manager.get("dynamicChain").getDescription());
        }

        @Test
        @DisplayName("reload 新链应注册新链")
        void shouldRegisterNewChainOnReload() {
            ChainDefinition updated = new ChainDefinition("newName");
            updated.setDescription("new");

            manager.reload("freshChain", updated);

            assertTrue(manager.contains("freshChain"));
        }

        @Test
        @DisplayName("reload null 参数应返回 null")
        void shouldReturnNullForNullParams() {
            assertNull(manager.reload(null, new ChainDefinition("test")));
            assertNull(manager.reload("test", null));
        }
    }

    @Nested
    @DisplayName("clearAll 清空方法")
    class ClearAllTests {

        @Test
        @DisplayName("clearAll 应清空所有注册的链")
        void shouldClearAllChains() {
            manager.register(new ChainDefinition("chain1"));
            manager.register(new ChainDefinition("chain2"));

            manager.clearAll();

            assertEquals(0, manager.size());
        }
    }

    @Nested
    @DisplayName("getChainMap 获取注册表")
    class GetChainMapTests {

        @Test
        @DisplayName("getChainMap 应返回不可变 Map")
        void shouldReturnUnmodifiableMap() {
            manager.register(new ChainDefinition("chain1"));

            assertThrows(UnsupportedOperationException.class,
                    () -> manager.getChainMap().put("chain2", new ChainDefinition("chain2")));
        }
    }
}
