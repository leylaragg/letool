package com.github.leyland.letool.rule.component;

import com.github.leyland.letool.rule.context.RuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeComponent 测试")
class NodeComponentTest {

    private RuleContext context;

    @BeforeEach
    void setUp() {
        context = new RuleContext("testChain");
    }

    @Nested
    @DisplayName("默认行为")
    class DefaultBehaviorTests {

        @Test
        @DisplayName("getName 默认应返回类名")
        void shouldReturnClassNameByDefault() {
            NodeComponent component = new TestComponent();
            assertEquals("TestComponent", component.getName());
        }

        @Test
        @DisplayName("condition 默认应返回 true")
        void shouldReturnTrueByDefault() {
            NodeComponent component = new TestComponent();
            assertTrue(component.condition(context));
        }

        @Test
        @DisplayName("init 默认不抛出异常")
        void initShouldNotThrow() {
            NodeComponent component = new TestComponent();
            assertDoesNotThrow(component::init);
        }

        @Test
        @DisplayName("destroy 默认不抛出异常")
        void destroyShouldNotThrow() {
            NodeComponent component = new TestComponent();
            assertDoesNotThrow(component::destroy);
        }
    }

    @Nested
    @DisplayName("自定义实现")
    class CustomImplementationTests {

        @Test
        @DisplayName("process 应由子类实现")
        void shouldExecuteProcess() {
            CountingComponent component = new CountingComponent();
            component.process(context);
            assertEquals(1, component.getExecutionCount());
        }

        @Test
        @DisplayName("重写 getName 应返回自定义名称")
        void shouldReturnCustomName() {
            NamedComponent component = new NamedComponent("customName");
            assertEquals("customName", component.getName());
        }

        @Test
        @DisplayName("重写 condition 应返回自定义条件结果")
        void shouldReturnCustomCondition() {
            ConditionalComponent component = new ConditionalComponent(false);
            assertFalse(component.condition(context));

            ConditionalComponent alwaysTrue = new ConditionalComponent(true);
            assertTrue(alwaysTrue.condition(context));
        }
    }

    // ======================== 测试用子类 ========================

    static class TestComponent extends NodeComponent {
        @Override
        public void process(RuleContext context) {
        }
    }

    static class CountingComponent extends NodeComponent {
        private int executionCount = 0;

        @Override
        public void process(RuleContext context) {
            executionCount++;
        }

        int getExecutionCount() {
            return executionCount;
        }
    }

    static class NamedComponent extends NodeComponent {
        private final String customName;

        NamedComponent(String customName) {
            this.customName = customName;
        }

        @Override
        public void process(RuleContext context) {
        }

        @Override
        public String getName() {
            return customName;
        }
    }

    static class ConditionalComponent extends NodeComponent {
        private final boolean returnValue;

        ConditionalComponent(boolean returnValue) {
            this.returnValue = returnValue;
        }

        @Override
        public void process(RuleContext context) {
        }

        @Override
        public boolean condition(RuleContext context) {
            return returnValue;
        }
    }
}
