package com.github.leyland.letool.pay.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PayOrder 支付订单模型测试")
class PayOrderTest {

    @Nested
    @DisplayName("Builder 基础构建测试")
    class BuilderBasicTests {

        @Test
        @DisplayName("应正确构建包含所有必填字段的订单")
        void shouldBuildWithRequiredFields() {
            PayOrder order = PayOrder.builder()
                    .outTradeNo("ORD-001")
                    .subject("测试商品")
                    .totalAmount(new BigDecimal("99.99"))
                    .channel(PayChannel.ALIPAY)
                    .build();

            assertEquals("ORD-001", order.getOutTradeNo());
            assertEquals("测试商品", order.getSubject());
            assertEquals(new BigDecimal("99.99"), order.getTotalAmount());
            assertEquals(PayChannel.ALIPAY, order.getChannel());
        }

        @Test
        @DisplayName("应正确构建包含所有字段的订单")
        void shouldBuildWithAllFields() {
            Map<String, String> extra = new HashMap<>();
            extra.put("key", "val");

            PayOrder order = PayOrder.builder()
                    .outTradeNo("ORD-002")
                    .subject("VIP会员")
                    .totalAmount(new BigDecimal("199.00"))
                    .currency("USD")
                    .channel(PayChannel.WECHAT)
                    .notifyUrl("https://example.com/notify")
                    .returnUrl("https://example.com/return")
                    .extra(extra)
                    .build();

            assertEquals("ORD-002", order.getOutTradeNo());
            assertEquals("VIP会员", order.getSubject());
            assertEquals(new BigDecimal("199.00"), order.getTotalAmount());
            assertEquals("USD", order.getCurrency());
            assertEquals(PayChannel.WECHAT, order.getChannel());
            assertEquals("https://example.com/notify", order.getNotifyUrl());
            assertEquals("https://example.com/return", order.getReturnUrl());
            assertEquals(extra, order.getExtra());
        }
    }

    @Nested
    @DisplayName("默认值测试")
    class DefaultsTests {

        @Test
        @DisplayName("未设置 currency 时应默认 CNY")
        void defaultCurrencyShouldBeCNY() {
            PayOrder order = PayOrder.builder()
                    .outTradeNo("ORD-001")
                    .subject("test")
                    .totalAmount(BigDecimal.ONE)
                    .channel(PayChannel.MOCK)
                    .build();

            assertEquals("CNY", order.getCurrency());
        }
    }

    @Nested
    @DisplayName("extra 扩展参数测试")
    class ExtraTests {

        @Test
        @DisplayName("extra(key, value) 应逐个添加入参")
        void extraByKeyValueShouldAddIndividually() {
            PayOrder order = PayOrder.builder()
                    .outTradeNo("ORD-001").subject("test")
                    .totalAmount(BigDecimal.ONE).channel(PayChannel.MOCK)
                    .extra("authCode", "123456")
                    .extra("scene", "bar_code")
                    .build();

            Map<String, String> extra = order.getExtra();
            assertNotNull(extra);
            assertEquals("123456", extra.get("authCode"));
            assertEquals("bar_code", extra.get("scene"));
        }

        @Test
        @DisplayName("extra(Map) 应设置整个扩展参数")
        void extraMapShouldSetAll() {
            Map<String, String> map = new HashMap<>();
            map.put("k1", "v1");
            map.put("k2", "v2");

            PayOrder order = PayOrder.builder()
                    .outTradeNo("ORD-001").subject("test")
                    .totalAmount(BigDecimal.ONE).channel(PayChannel.MOCK)
                    .extra(map)
                    .build();

            assertEquals(2, order.getExtra().size());
        }
    }

    @Nested
    @DisplayName("Builder 链式调用测试")
    class BuilderChainingTests {

        @Test
        @DisplayName("所有 Builder 方法应返回自身")
        void allBuilderMethodsShouldReturnThis() {
            PayOrder.Builder builder = PayOrder.builder();
            assertSame(builder, builder.outTradeNo("ORD-001"));
            assertSame(builder, builder.subject("test"));
            assertSame(builder, builder.totalAmount(BigDecimal.ONE));
            assertSame(builder, builder.currency("CNY"));
            assertSame(builder, builder.channel(PayChannel.MOCK));
            assertSame(builder, builder.notifyUrl("url"));
            assertSame(builder, builder.returnUrl("url"));
            assertSame(builder, builder.extra("k", "v"));
            Map<String, String> map = new HashMap<>();
            assertSame(builder, builder.extra(map));
        }
    }
}
