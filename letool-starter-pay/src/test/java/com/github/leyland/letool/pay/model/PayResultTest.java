package com.github.leyland.letool.pay.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PayResult 支付结果模型测试")
class PayResultTest {

    @Nested
    @DisplayName("全参数构造函数测试")
    class FullConstructorTests {

        @Test
        @DisplayName("应正确初始化所有字段")
        void shouldInitializeAllFields() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("sign", "abc123");

            PayResult result = new PayResult(true, "ORD-001", "TXN-001",
                    PayStatus.SUCCESS, new BigDecimal("100.00"), "CH-001",
                    null, null, raw);

            assertTrue(result.isSuccess());
            assertEquals("ORD-001", result.getOutTradeNo());
            assertEquals("TXN-001", result.getTransactionId());
            assertEquals(PayStatus.SUCCESS, result.getStatus());
            assertEquals(new BigDecimal("100.00"), result.getTotalAmount());
            assertEquals("CH-001", result.getChannelOrderNo());
            assertNull(result.getErrorCode());
            assertNull(result.getErrorMessage());
            assertEquals("abc123", result.getRaw().get("sign"));
        }

        @Test
        @DisplayName("raw 为 null 时应初始化为空 Map")
        void nullRawShouldBeEmptyMap() {
            PayResult result = new PayResult(false, null, null, null,
                    null, null, "E001", "error", null);
            assertNotNull(result.getRaw());
            assertTrue(result.getRaw().isEmpty());
        }

        @Test
        @DisplayName("raw 应不可修改")
        void rawShouldBeUnmodifiable() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("k", "v");
            PayResult result = new PayResult(true, null, null, null,
                    null, null, null, null, raw);

            Map<String, Object> returnedRaw = result.getRaw();
            assertThrows(UnsupportedOperationException.class, () -> returnedRaw.put("new", "val"));
        }

        @Test
        @DisplayName("构造后修改原始 raw 不影响 PayResult")
        void modifyingOriginalRawShouldNotAffectResult() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("k", "v");
            PayResult result = new PayResult(true, null, null, null,
                    null, null, null, null, raw);
            raw.put("hacked", true);

            assertFalse(result.getRaw().containsKey("hacked"));
            assertEquals(1, result.getRaw().size());
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryTests {

        @Test
        @DisplayName("success() 应创建成功结果")
        void successFactoryShouldCreateSuccessResult() {
            PayResult result = PayResult.success("ORD-001", "TXN-001", new BigDecimal("50.00"));

            assertTrue(result.isSuccess());
            assertEquals("ORD-001", result.getOutTradeNo());
            assertEquals("TXN-001", result.getTransactionId());
            assertEquals(PayStatus.SUCCESS, result.getStatus());
            assertEquals(new BigDecimal("50.00"), result.getTotalAmount());
            assertNull(result.getErrorCode());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("fail() 应创建失败结果")
        void failFactoryShouldCreateFailResult() {
            PayResult result = PayResult.fail("E001", "余额不足");

            assertFalse(result.isSuccess());
            assertEquals("E001", result.getErrorCode());
            assertEquals("余额不足", result.getErrorMessage());
            assertNull(result.getOutTradeNo());
            assertNull(result.getTransactionId());
        }

        @Test
        @DisplayName("fromCallback() 应解析回调参数")
        void fromCallbackShouldParseParams() {
            Map<String, Object> callbackParams = new HashMap<>();
            callbackParams.put("trade_no", "T001");
            callbackParams.put("total_amount", "100.00");

            PayResult result = PayResult.fromCallback(callbackParams);

            assertTrue(result.isSuccess());
            assertEquals(2, result.getRaw().size());
            assertEquals("T001", result.getRaw().get("trade_no"));
        }
    }

    @Nested
    @DisplayName("错误信息测试")
    class ErrorInfoTests {

        @Test
        @DisplayName("成功结果的 errorCode/errorMessage 应为 null")
        void successResultShouldHaveNullErrors() {
            PayResult result = PayResult.success("ORD-001", "TXN-001", BigDecimal.ONE);
            assertNull(result.getErrorCode());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("失败结果应有 errorCode 和 errorMessage")
        void failResultShouldHaveErrors() {
            PayResult result = PayResult.fail("E_AUTH", "签名验证失败");
            assertEquals("E_AUTH", result.getErrorCode());
            assertEquals("签名验证失败", result.getErrorMessage());
        }
    }
}
