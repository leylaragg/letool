package com.github.leyland.letool.rule.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuleResult 测试")
class RuleResultTest {

    @Nested
    @DisplayName("success 工厂方法")
    class SuccessTests {

        @Test
        @DisplayName("success 应创建成功结果")
        void shouldCreateSuccessResult() {
            List<RuleContext.NodeTrace> traces = new ArrayList<>();
            RuleContext.NodeTrace trace = new RuleContext.NodeTrace("nodeA");
            trace.complete();
            traces.add(trace);

            RuleResult result = RuleResult.success("riskChain", "exec-001", traces);

            assertTrue(result.isSuccess());
            assertEquals("riskChain", result.getChainName());
            assertEquals("exec-001", result.getExecutionId());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("success 应自动计算总耗时")
        void shouldCalculateTotalDuration() {
            List<RuleContext.NodeTrace> traces = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                RuleContext.NodeTrace trace = new RuleContext.NodeTrace("node" + i);
                trace.complete();
                traces.add(trace);
            }

            RuleResult result = RuleResult.success("chain", "id", traces);
            assertTrue(result.getTotalDurationMs() >= 0);
        }

        @Test
        @DisplayName("空 traces 应正常处理")
        void shouldHandleEmptyTraces() {
            RuleResult result = RuleResult.success("chain", "id", Collections.emptyList());

            assertTrue(result.isSuccess());
            assertEquals(0, result.getTotalDurationMs());
        }
    }

    @Nested
    @DisplayName("fail 工厂方法")
    class FailTests {

        @Test
        @DisplayName("fail 应创建失败结果并记录错误信息")
        void shouldCreateFailResult() {
            List<RuleContext.NodeTrace> traces = new ArrayList<>();
            RuleResult result = RuleResult.fail("riskChain", "exec-002", "节点执行超时", traces);

            assertFalse(result.isSuccess());
            assertEquals("节点执行超时", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("getter/setter 操作")
    class GetterSetterTests {

        @Test
        @DisplayName("setOutputs 应正确存储输出数据")
        void shouldStoreOutputs() {
            List<RuleContext.NodeTrace> traces = new ArrayList<>();
            RuleResult result = RuleResult.success("chain", "id", traces);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("riskScore", 85);
            outputs.put("approved", true);
            result.setOutputs(outputs);

            assertEquals(85, result.getOutputs().get("riskScore"));
            assertEquals(true, result.getOutputs().get("approved"));
        }

        @Test
        @DisplayName("getOutputs 应返回不可变副本")
        void shouldReturnUnmodifiableOutputs() {
            RuleResult result = RuleResult.success("chain", "id", Collections.emptyList());
            Map<String, Object> outputs = result.getOutputs();

            assertThrows(UnsupportedOperationException.class, () -> outputs.put("key", "val"));
        }

        @Test
        @DisplayName("setTotalDurationMs 应覆盖计算的耗时")
        void shouldOverrideDuration() {
            RuleResult result = RuleResult.success("chain", "id", Collections.emptyList());
            result.setTotalDurationMs(1500);

            assertEquals(1500, result.getTotalDurationMs());
        }

        @Test
        @DisplayName("setErrorMessage 应设置错误信息")
        void shouldSetErrorMessage() {
            RuleResult result = RuleResult.success("chain", "id", Collections.emptyList());
            result.setErrorMessage("手动设置的错误");

            assertEquals("手动设置的错误", result.getErrorMessage());
        }
    }
}
