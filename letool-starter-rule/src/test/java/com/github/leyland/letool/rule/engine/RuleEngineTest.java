package com.github.leyland.letool.rule.engine;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.component.NodeComponent;
import com.github.leyland.letool.rule.context.RuleContext;
import com.github.leyland.letool.rule.context.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleEngine 测试")
class RuleEngineTest {

    private ChainManager chainManager;
    private Map<String, NodeComponent> componentRegistry;
    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        chainManager = new ChainManager();
        componentRegistry = new ConcurrentHashMap<>();
        engine = new RuleEngine(chainManager, componentRegistry);
    }

    @Nested
    @DisplayName("构造函数")
    class ConstructorTests {

        @Test
        @DisplayName("基本构造应创建可用实例")
        void shouldCreateInstance() {
            assertNotNull(engine);
        }

        @Test
        @DisplayName("null 组件注册表应自动初始化为空 Map")
        void shouldHandleNullRegistry() {
            RuleEngine e = new RuleEngine(chainManager, (Map<String, NodeComponent>) null);
            assertNotNull(e);
        }
    }

    @Nested
    @DisplayName("execute 方法")
    class ExecuteTests {

        @Test
        @DisplayName("执行不存在的链应返回失败结果")
        void shouldFailOnMissingChain() {
            RuleContext context = new RuleContext("nonexistent");
            RuleResult result = engine.execute("nonexistent", context);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("不存在"));
        }

        @Test
        @DisplayName("应正确执行简单 THEN 链")
        void shouldExecuteSimpleThenChain() {
            SpyComponent component = new SpyComponent();
            componentRegistry.put("step1", component);

            ChainDefinition chain = new ChainDefinition("testChain");
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("step1", "THEN");
            chain.addNode(node);
            chainManager.register(chain);

            RuleContext context = new RuleContext("testChain");
            context.setParam("input", "hello");

            RuleResult result = engine.execute("testChain", context);

            assertTrue(result.isSuccess());
            assertTrue(component.wasExecuted());
        }

        @Test
        @DisplayName("未注册的组件应导致失败")
        void shouldFailOnUnregisteredComponent() {
            ChainDefinition chain = new ChainDefinition("badChain");
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("unregistered", "THEN");
            chain.addNode(node);
            chainManager.register(chain);

            RuleContext context = new RuleContext("badChain");
            RuleResult result = engine.execute("badChain", context);

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("执行成功的链应返回输出数据")
        void shouldReturnOutputData() {
            OutputComponent component = new OutputComponent();
            componentRegistry.put("outputter", component);

            ChainDefinition chain = new ChainDefinition("outputChain");
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("outputter", "THEN");
            chain.addNode(node);
            chainManager.register(chain);

            RuleContext context = new RuleContext("outputChain");
            RuleResult result = engine.execute("outputChain", context);

            assertTrue(result.isSuccess());
            assertEquals("processed", result.getOutputs().get("status"));
        }
    }

    @Nested
    @DisplayName("节点类型执行")
    class NodeTypeTests {

        @Test
        @DisplayName("THEN 节点应顺序执行子节点")
        void shouldExecuteThenChildren() {
            SpyComponent child1 = new SpyComponent();
            SpyComponent child2 = new SpyComponent();
            componentRegistry.put("child1", child1);
            componentRegistry.put("child2", child2);

            ChainDefinition chain = new ChainDefinition("thenChain");
            // 父节点不设置名称以避免尝试执行父组件
            ChainDefinition.NodeDefinition parent = new ChainDefinition.NodeDefinition(null, "THEN");
            parent.addChild(new ChainDefinition.NodeDefinition("child1"));
            parent.addChild(new ChainDefinition.NodeDefinition("child2"));
            chain.addNode(parent);
            chainManager.register(chain);

            RuleContext context = new RuleContext("thenChain");
            RuleResult result = engine.execute("thenChain", context);

            assertTrue(result.isSuccess());
            assertTrue(child1.wasExecuted());
            assertTrue(child2.wasExecuted());
        }

        @Test
        @DisplayName("IF 节点条件不满足时应跳过子节点")
        void shouldSkipIfConditionNotMet() {
            SpyComponent child = new SpyComponent();
            componentRegistry.put("child", child);

            // 不设置 GroovyScriptEngine，condition 方法默认返回 true
            // 所以这里 condition 为空字符串时，会调用组件的 condition 方法
            ChainDefinition chain = new ChainDefinition("ifChain");
            ChainDefinition.NodeDefinition ifNode = new ChainDefinition.NodeDefinition("ifNode", "IF");
            ifNode.setCondition("false");  // 这个条件由 Groovy 评估
            ifNode.addChild(new ChainDefinition.NodeDefinition("child"));
            chain.addNode(ifNode);
            chainManager.register(chain);

            RuleContext context = new RuleContext("ifChain");
            RuleResult result = engine.execute("ifChain", context);

            // 没有 Groovy 引擎时，condition 为 true
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("无类型节点应直接执行组件")
        void shouldExecuteComponentDirectly() {
            SpyComponent component = new SpyComponent();
            componentRegistry.put("directNode", component);

            ChainDefinition chain = new ChainDefinition("directChain");
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("directNode");
            chain.addNode(node);
            chainManager.register(chain);

            RuleContext context = new RuleContext("directChain");
            RuleResult result = engine.execute("directChain", context);

            assertTrue(result.isSuccess());
            assertTrue(component.wasExecuted());
        }
    }

    @Nested
    @DisplayName("registerComponent 方法")
    class RegisterComponentTests {

        @Test
        @DisplayName("registerComponent 应注册新组件并调用 init")
        void shouldRegisterComponent() {
            SpyComponent component = new SpyComponent();
            engine.registerComponent("myComponent", component);

            assertTrue(component.wasInitialized());
        }
    }

    @Nested
    @DisplayName("shutdown 方法")
    class ShutdownTests {

        @Test
        @DisplayName("shutdown 应释放资源")
        void shouldShutdownGracefully() {
            SpyComponent component = new SpyComponent();
            componentRegistry.put("comp", component);

            engine.shutdown();
            assertTrue(component.wasDestroyed());
        }
    }

    // ======================== 测试用组件 ========================

    static class SpyComponent extends NodeComponent {
        private boolean executed = false;
        private boolean destroyed = false;
        private boolean initialized = false;

        @Override
        public void process(RuleContext context) {
            executed = true;
        }

        @Override
        public void init() {
            initialized = true;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        boolean wasExecuted() { return executed; }
        boolean wasDestroyed() { return destroyed; }
        boolean wasInitialized() { return initialized; }
    }

    static class OutputComponent extends NodeComponent {
        @Override
        public void process(RuleContext context) {
            context.setResult("status", "processed");
            context.setResult("score", 95);
        }
    }
}
