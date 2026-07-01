package com.github.leyland.letool.rule.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleContext 测试")
class RuleContextTest {

    private RuleContext context;

    @BeforeEach
    void setUp() {
        context = new RuleContext("testChain");
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructionTests {

        @Test
        @DisplayName("基本构造应设置 chainName 和生成 executionId")
        void shouldSetChainNameAndGenerateExecutionId() {
            assertEquals("testChain", context.getChainName());
            assertNotNull(context.getExecutionId());
            assertTrue(context.getExecutionId().length() > 0);
        }

        @Test
        @DisplayName("带初始参数的构造应包含传入的参数")
        void shouldAcceptInitialParams() {
            Map<String, Object> initialParams = new HashMap<>();
            initialParams.put("userId", 1001);
            initialParams.put("amount", 50000);

            RuleContext ctx = new RuleContext("riskChain", initialParams);

            assertEquals(1001, ctx.getParam("userId"));
            assertEquals(50000, ctx.getParam("amount"));
        }

        @Test
        @DisplayName("带 null 初始参数时应正常工作")
        void shouldHandleNullInitialParams() {
            RuleContext ctx = new RuleContext("riskChain", null);
            assertNotNull(ctx.getExecutionId());
            assertEquals("riskChain", ctx.getChainName());
        }
    }

    @Nested
    @DisplayName("参数操作")
    class ParamTests {

        @Test
        @DisplayName("setParam 和 getParam 应正确存取")
        void shouldSetAndGetParam() {
            context.setParam("key", "value");
            assertEquals("value", context.getParam("key"));
        }

        @Test
        @DisplayName("getParam 获取不存在的键应返回 null")
        void shouldReturnNullForMissingParam() {
            assertNull(context.getParam("nonexistent"));
        }

        @Test
        @DisplayName("getParam 带类型参数应返回类型转换后的值")
        void shouldGetParamWithType() {
            context.setParam("count", 42);
            Integer count = context.getParam("count", Integer.class);
            assertEquals(42, count);
        }

        @Test
        @DisplayName("getParams 应返回不可变副本")
        void shouldReturnUnmodifiableParams() {
            context.setParam("key1", "val1");
            Map<String, Object> params = context.getParams();

            assertThrows(UnsupportedOperationException.class, () -> params.put("key2", "val2"));
        }
    }

    @Nested
    @DisplayName("结果操作")
    class ResultTests {

        @Test
        @DisplayName("setResult 和 getResult 应正确存取")
        void shouldSetAndGetResult() {
            context.setResult("score", 85);
            assertEquals(85, context.getResult("score"));
        }

        @Test
        @DisplayName("节点间通过 results 传递中间数据")
        void shouldShareResultsBetweenNodes() {
            context.setResult("riskLevel", "HIGH");
            assertEquals("HIGH", context.getResult("riskLevel"));

            context.setResult("approved", false);
            assertFalse((Boolean) context.getResult("approved"));
        }
    }

    @Nested
    @DisplayName("执行追踪")
    class TraceTests {

        @Test
        @DisplayName("addTrace 应记录执行轨迹")
        void shouldAddTrace() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("nodeA");
            trace.complete();
            context.addTrace(trace);

            assertEquals(1, context.getTraces().size());
            assertEquals("nodeA", context.getTraces().get(0).getNodeName());
            assertTrue(context.getTraces().get(0).isSuccess());
        }

        @Test
        @DisplayName("轨迹应记录失败状态")
        void shouldRecordFailureTrace() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("nodeB");
            trace.fail("执行超时");
            context.addTrace(trace);

            assertFalse(context.getTraces().get(0).isSuccess());
            assertEquals("执行超时", context.getTraces().get(0).getErrorMessage());
        }

        @Test
        @DisplayName("多个节点应产生多条轨迹")
        void shouldRecordMultipleTraces() {
            for (int i = 0; i < 5; i++) {
                RuleContext.NodeTrace trace = new RuleContext.NodeTrace("node" + i);
                trace.complete();
                context.addTrace(trace);
            }

            assertEquals(5, context.getTraces().size());
        }
    }

    @Nested
    @DisplayName("NodeTrace 内部类")
    class NodeTraceTests {

        @Test
        @DisplayName("创建后应记录开始时间")
        void shouldRecordStartTime() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("test");

            assertEquals("test", trace.getNodeName());
            assertTrue(trace.getStartTime() > 0);
        }

        @Test
        @DisplayName("complete 后应记录结束时间且 success 为 true")
        void shouldCompleteSuccessfully() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("test");
            trace.complete();

            assertTrue(trace.isSuccess());
            assertTrue(trace.getEndTime() > 0);
            assertNull(trace.getErrorMessage());
        }

        @Test
        @DisplayName("fail 后 success 应为 false 且记录错误信息")
        void shouldFailWithErrorMessage() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("test");
            trace.fail("节点执行异常");

            assertFalse(trace.isSuccess());
            assertEquals("节点执行异常", trace.getErrorMessage());
        }

        @Test
        @DisplayName("getDurationMs 应返回耗时")
        void shouldCalculateDuration() throws InterruptedException {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("test");
            Thread.sleep(10);
            trace.complete();

            assertTrue(trace.getDurationMs() >= 10);
        }

        @Test
        @DisplayName("input/output 快照应正确存取")
        void shouldStoreInputOutput() {
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("test");

            Map<String, Object> input = new HashMap<>();
            input.put("age", 25);
            trace.setInput(input);

            Map<String, Object> output = new HashMap<>();
            output.put("result", "PASS");
            trace.setOutput(output);

            assertEquals(25, trace.getInput().get("age"));
            assertEquals("PASS", trace.getOutput().get("result"));
        }
    }
}
