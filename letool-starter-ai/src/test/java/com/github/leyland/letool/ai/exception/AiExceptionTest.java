package com.github.leyland.letool.ai.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiException AI 异常测试")
class AiExceptionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("(message, provider) 构造")
        void constructorMessageProvider() {
            AiException ex = new AiException("API 调用失败", "openai");

            assertEquals("API 调用失败", ex.getMessage());
            assertEquals("openai", ex.getProvider());
            assertEquals(0, ex.getStatusCode());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, provider, cause) 带原始异常")
        void constructorWithCause() {
            IOException cause = new IOException("连接超时");
            AiException ex = new AiException("请求失败", "deepseek", cause);

            assertEquals("请求失败", ex.getMessage());
            assertEquals("deepseek", ex.getProvider());
            assertEquals(0, ex.getStatusCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("(statusCode, message, provider) 带状态码")
        void constructorWithStatusCode() {
            AiException ex = new AiException(429, "请求频率超限", "openai");

            assertEquals("请求频率超限", ex.getMessage());
            assertEquals("openai", ex.getProvider());
            assertEquals(429, ex.getStatusCode());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(statusCode, message, provider, cause) 完整参数")
        void constructorFull() {
            IOException cause = new IOException("网络错误");
            AiException ex = new AiException(500, "服务器内部错误", "azure", cause);

            assertEquals("服务器内部错误", ex.getMessage());
            assertEquals("azure", ex.getProvider());
            assertEquals(500, ex.getStatusCode());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("isRateLimitExceeded 速率限制判断")
    class RateLimitTests {

        @Test
        @DisplayName("状态码 429 返回 true")
        void rateLimitExceededTrue() {
            AiException ex = new AiException(429, "rate limited", "openai");
            assertTrue(ex.isRateLimitExceeded());
        }

        @Test
        @DisplayName("状态码 200 返回 false")
        void rateLimitExceededFalse200() {
            AiException ex = new AiException(200, "ok", "openai");
            assertFalse(ex.isRateLimitExceeded());
        }

        @Test
        @DisplayName("状态码 0（非 HTTP 错误）返回 false")
        void rateLimitExceededFalseZero() {
            AiException ex = new AiException("网络错误", "openai");
            assertFalse(ex.isRateLimitExceeded());
        }

        @Test
        @DisplayName("状态码 500 返回 false")
        void rateLimitExceededFalse500() {
            AiException ex = new AiException(500, "server error", "openai");
            assertFalse(ex.isRateLimitExceeded());
        }
    }

    @Nested
    @DisplayName("isAuthError 认证错误判断")
    class AuthErrorTests {

        @Test
        @DisplayName("状态码 401 返回 true")
        void authError401() {
            AiException ex = new AiException(401, "unauthorized", "openai");
            assertTrue(ex.isAuthError());
        }

        @Test
        @DisplayName("状态码 403 返回 true")
        void authError403() {
            AiException ex = new AiException(403, "forbidden", "openai");
            assertTrue(ex.isAuthError());
        }

        @Test
        @DisplayName("状态码 200 返回 false")
        void authErrorFalse200() {
            AiException ex = new AiException(200, "ok", "openai");
            assertFalse(ex.isAuthError());
        }

        @Test
        @DisplayName("状态码 429 返回 false")
        void authErrorFalse429() {
            AiException ex = new AiException(429, "rate limited", "openai");
            assertFalse(ex.isAuthError());
        }

        @Test
        @DisplayName("状态码 0 返回 false")
        void authErrorFalseZero() {
            AiException ex = new AiException("网络错误", "openai");
            assertFalse(ex.isAuthError());
        }
    }

    @Nested
    @DisplayName("getProvider / getStatusCode")
    class GetterTests {

        @Test
        @DisplayName("getProvider 返回提供商")
        void getProvider() {
            AiException ex = new AiException("error", "qwen");
            assertEquals("qwen", ex.getProvider());
        }

        @Test
        @DisplayName("getStatusCode 返回状态码")
        void getStatusCode() {
            AiException ex = new AiException(404, "not found", "openai");
            assertEquals(404, ex.getStatusCode());
        }

        @Test
        @DisplayName("非 HTTP 错误 getStatusCode 返回 0")
        void getStatusCodeZero() {
            AiException ex = new AiException("timeout", "openai", new RuntimeException());
            assertEquals(0, ex.getStatusCode());
        }
    }
}
