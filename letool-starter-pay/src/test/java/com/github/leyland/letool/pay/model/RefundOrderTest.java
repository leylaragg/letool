package com.github.leyland.letool.pay.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RefundOrder 退款订单模型测试")
class RefundOrderTest {

    @Nested
    @DisplayName("Builder 基础构建测试")
    class BuilderBasicTests {

        @Test
        @DisplayName("应正确构建包含所有必填字段的退款订单")
        void shouldBuildWithRequiredFields() {
            RefundOrder order = RefundOrder.builder()
                    .outTradeNo("ORD-001")
                    .outRefundNo("RFD-001")
                    .refundAmount(new BigDecimal("50.00"))
                    .channel(PayChannel.ALIPAY)
                    .build();

            assertEquals("ORD-001", order.getOutTradeNo());
            assertEquals("RFD-001", order.getOutRefundNo());
            assertEquals(new BigDecimal("50.00"), order.getRefundAmount());
            assertEquals(PayChannel.ALIPAY, order.getChannel());
        }

        @Test
        @DisplayName("应正确构建包含退款原因的完整订单")
        void shouldBuildWithAllFields() {
            RefundOrder order = RefundOrder.builder()
                    .outTradeNo("ORD-002")
                    .outRefundNo("RFD-002")
                    .refundAmount(new BigDecimal("199.99"))
                    .refundReason("用户申请退款")
                    .channel(PayChannel.WECHAT)
                    .build();

            assertEquals("ORD-002", order.getOutTradeNo());
            assertEquals("RFD-002", order.getOutRefundNo());
            assertEquals(new BigDecimal("199.99"), order.getRefundAmount());
            assertEquals("用户申请退款", order.getRefundReason());
            assertEquals(PayChannel.WECHAT, order.getChannel());
        }
    }

    @Nested
    @DisplayName("可选字段测试")
    class OptionalFieldsTests {

        @Test
        @DisplayName("未设置 refundReason 时应为 null")
        void missingRefundReasonShouldBeNull() {
            RefundOrder order = RefundOrder.builder()
                    .outTradeNo("ORD-001")
                    .outRefundNo("RFD-001")
                    .refundAmount(BigDecimal.ONE)
                    .channel(PayChannel.MOCK)
                    .build();

            assertNull(order.getRefundReason());
        }
    }

    @Nested
    @DisplayName("Builder 链式调用测试")
    class BuilderChainingTests {

        @Test
        @DisplayName("所有 Builder 方法应返回自身")
        void allBuilderMethodsShouldReturnThis() {
            RefundOrder.Builder builder = RefundOrder.builder();
            assertSame(builder, builder.outTradeNo("ORD-001"));
            assertSame(builder, builder.outRefundNo("RFD-001"));
            assertSame(builder, builder.refundAmount(BigDecimal.ONE));
            assertSame(builder, builder.refundReason("reason"));
            assertSame(builder, builder.channel(PayChannel.MOCK));
        }
    }
}
