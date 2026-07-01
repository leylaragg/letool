package com.github.leyland.letool.monitor.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("告警模型测试")
class AlertModelsTest {

    @Nested
    @DisplayName("AlertLevel 告警级别")
    class AlertLevelTests {

        @Test
        @DisplayName("枚举值 WARN")
        void warn() {
            assertEquals("WARN", AlertLevel.WARN.name());
        }

        @Test
        @DisplayName("枚举值 CRITICAL")
        void critical() {
            assertEquals("CRITICAL", AlertLevel.CRITICAL.name());
        }

        @Test
        @DisplayName("values() 包含两个值")
        void valuesCount() {
            assertEquals(2, AlertLevel.values().length);
        }
    }

    @Nested
    @DisplayName("AlertCondition 告警条件")
    class AlertConditionTests {

        @Test
        @DisplayName("GREATER_THAN 符号为 >")
        void greaterThanSymbol() {
            assertEquals(">", AlertCondition.GREATER_THAN.getSymbol());
        }

        @Test
        @DisplayName("LESS_THAN 符号为 <")
        void lessThanSymbol() {
            assertEquals("<", AlertCondition.LESS_THAN.getSymbol());
        }

        @Test
        @DisplayName("EQUAL 符号为 =")
        void equalSymbol() {
            assertEquals("=", AlertCondition.EQUAL.getSymbol());
        }

        @Test
        @DisplayName("values() 包含三个值")
        void valuesCount() {
            assertEquals(3, AlertCondition.values().length);
        }
    }

    @Nested
    @DisplayName("AlertRule 告警规则")
    class AlertRuleTests {

        @Test
        @DisplayName("默认构造函数设置默认值")
        void defaultConstructor() {
            AlertRule rule = new AlertRule();
            assertNull(rule.getName());
            assertEquals(AlertCondition.GREATER_THAN, rule.getCondition());
            assertEquals(0.0, rule.getThreshold(), 0.001);
            assertEquals(AlertLevel.WARN, rule.getLevel());
            assertNotNull(rule.getNotifierTypes());
            assertTrue(rule.getNotifierTypes().isEmpty());
        }

        @Test
        @DisplayName("完整参数构造函数")
        void fullConstructor() {
            AlertRule rule = new AlertRule(
                    "heap-warning", "heap.used.percent", AlertCondition.GREATER_THAN,
                    0.85, "5m", AlertLevel.CRITICAL, "堆内存过高: {value}%",
                    List.of("dingtalk", "mail"));

            assertEquals("heap-warning", rule.getName());
            assertEquals("heap.used.percent", rule.getMetric());
            assertEquals(AlertCondition.GREATER_THAN, rule.getCondition());
            assertEquals(0.85, rule.getThreshold(), 0.001);
            assertEquals("5m", rule.getDuration());
            assertEquals(AlertLevel.CRITICAL, rule.getLevel());
            assertEquals("堆内存过高: {value}%", rule.getMessage());
            assertEquals(2, rule.getNotifierTypes().size());
        }

        @Test
        @DisplayName("getter / setter 方法")
        void getterSetter() {
            AlertRule rule = new AlertRule();
            rule.setName("cpu-warning");
            rule.setMetric("cpu.usage");
            rule.setCondition(AlertCondition.LESS_THAN);
            rule.setThreshold(0.10);
            rule.setDuration("10s");
            rule.setLevel(AlertLevel.WARN);
            rule.setMessage("CPU usage low");
            rule.setNotifierTypes(List.of("wechat"));

            assertEquals("cpu-warning", rule.getName());
            assertEquals("cpu.usage", rule.getMetric());
            assertEquals(AlertCondition.LESS_THAN, rule.getCondition());
            assertEquals(0.10, rule.getThreshold(), 0.001);
            assertEquals("10s", rule.getDuration());
            assertEquals(AlertLevel.WARN, rule.getLevel());
            assertEquals("CPU usage low", rule.getMessage());
            assertEquals(1, rule.getNotifierTypes().size());
        }

        @Test
        @DisplayName("isTriggered GREATER_THAN 条件")
        void isTriggeredGreaterThan() {
            AlertRule rule = new AlertRule();
            rule.setCondition(AlertCondition.GREATER_THAN);
            rule.setThreshold(0.85);

            assertTrue(rule.isTriggered(0.90));
            assertFalse(rule.isTriggered(0.80));
            assertFalse(rule.isTriggered(0.85));
        }

        @Test
        @DisplayName("isTriggered LESS_THAN 条件")
        void isTriggeredLessThan() {
            AlertRule rule = new AlertRule();
            rule.setCondition(AlertCondition.LESS_THAN);
            rule.setThreshold(0.10);

            assertTrue(rule.isTriggered(0.05));
            assertFalse(rule.isTriggered(0.15));
        }

        @Test
        @DisplayName("isTriggered EQUAL 条件（浮点容差）")
        void isTriggeredEqual() {
            AlertRule rule = new AlertRule();
            rule.setCondition(AlertCondition.EQUAL);
            rule.setThreshold(1.0);

            assertTrue(rule.isTriggered(1.0));
            assertTrue(rule.isTriggered(1.00005));
            assertFalse(rule.isTriggered(1.1));
            assertFalse(rule.isTriggered(0.9));
        }

        @Test
        @DisplayName("formatMessage 模板替换")
        void formatMessage() {
            AlertRule rule = new AlertRule();
            rule.setMetric("heap.used.percent");
            rule.setThreshold(0.85);
            rule.setLevel(AlertLevel.CRITICAL);
            rule.setCondition(AlertCondition.GREATER_THAN);
            rule.setMessage("指标 {metric} 当前值 {value} 超过阈值 {threshold}");

            String formatted = rule.formatMessage(0.90);
            assertTrue(formatted.contains("heap.used.percent"));
            assertTrue(formatted.contains("0.90"));
            assertTrue(formatted.contains("0.85"));
        }

        @Test
        @DisplayName("formatMessage message 为 null 时使用默认格式")
        void formatMessageNullMessage() {
            AlertRule rule = new AlertRule();
            rule.setMetric("cpu.usage");
            rule.setThreshold(0.90);
            rule.setLevel(AlertLevel.CRITICAL);
            rule.setCondition(AlertCondition.GREATER_THAN);
            rule.setMessage(null);

            String formatted = rule.formatMessage(0.95);
            assertTrue(formatted.contains("CRITICAL"));
            assertTrue(formatted.contains("cpu.usage"));
            assertTrue(formatted.contains("0.95"));
        }

        @Test
        @DisplayName("toString 包含关键信息")
        void toStringContainsKeyInfo() {
            AlertRule rule = new AlertRule();
            rule.setName("test-rule");
            rule.setMetric("test.metric");
            rule.setLevel(AlertLevel.WARN);

            String str = rule.toString();
            assertTrue(str.contains("test-rule"));
            assertTrue(str.contains("test.metric"));
            assertTrue(str.contains("WARN"));
        }
    }
}
