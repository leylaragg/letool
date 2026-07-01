package com.github.leyland.letool.pay.exception;

import com.github.leyland.letool.pay.model.PayChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PayException 支付异常测试")
class PayExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message) 构造应正确设置消息")
        void singleArgConstructorShouldSetMessage() {
            PayException ex = new PayException("支付失败");
            assertEquals("支付失败", ex.getMessage());
            assertNull(ex.getOutTradeNo());
            assertNull(ex.getChannel());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause) 构造应正确设置消息和原因")
        void twoArgConstructorShouldSetMessageAndCause() {
            RuntimeException cause = new RuntimeException("网络超时");
            PayException ex = new PayException("支付失败", cause);
            assertEquals("支付失败", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertNull(ex.getOutTradeNo());
            assertNull(ex.getChannel());
        }

        @Test
        @DisplayName("(message, outTradeNo, channel) 构造应正确设置订单信息")
        void threeArgConstructorShouldSetOrderInfo() {
            PayException ex = new PayException("订单不存在", "ORD-001", PayChannel.WECHAT);
            assertEquals("订单不存在", ex.getMessage());
            assertEquals("ORD-001", ex.getOutTradeNo());
            assertEquals(PayChannel.WECHAT, ex.getChannel());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, cause, outTradeNo, channel) 构造应正确设置所有字段")
        void fourArgConstructorShouldSetAllFields() {
            RuntimeException cause = new RuntimeException("签名验证失败");
            PayException ex = new PayException("支付宝支付失败", cause, "ORD-002", PayChannel.ALIPAY);
            assertEquals("支付宝支付失败", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertEquals("ORD-002", ex.getOutTradeNo());
            assertEquals(PayChannel.ALIPAY, ex.getChannel());
        }
    }

    @Nested
    @DisplayName("继承体系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            PayException ex = new PayException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("getOutTradeNo/getChannel 测试")
    class GetterTests {

        @Test
        @DisplayName("无订单信息的异常应返回 null")
        void exceptionsWithoutOrderInfoShouldReturnNull() {
            PayException ex = new PayException("通用错误");
            assertNull(ex.getOutTradeNo());
            assertNull(ex.getChannel());
        }

        @Test
        @DisplayName("带订单信息的异常应返回正确值")
        void exceptionsWithOrderInfoShouldReturnCorrectValues() {
            PayException ex = new PayException("支付超时", "ORD-003", PayChannel.UNION);
            assertEquals("ORD-003", ex.getOutTradeNo());
            assertEquals(PayChannel.UNION, ex.getChannel());
        }

        @Test
        @DisplayName("不同异常实例的订单信息应独立")
        void orderInfoShouldBeIndependent() {
            PayException ex1 = new PayException("m1", "ORD-A", PayChannel.ALIPAY);
            PayException ex2 = new PayException("m2", "ORD-B", PayChannel.WECHAT);
            assertEquals("ORD-A", ex1.getOutTradeNo());
            assertEquals("ORD-B", ex2.getOutTradeNo());
        }
    }
}
