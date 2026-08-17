package io.github.leylaragg.letool.ratelimiter.aspect;

import io.github.leylaragg.letool.ratelimiter.annotation.RateLimit;
import io.github.leylaragg.letool.ratelimiter.config.RateLimiterProperties;
import io.github.leylaragg.letool.ratelimiter.core.RateLimitResult;
import io.github.leylaragg.letool.ratelimiter.core.RateLimitTemplate;
import io.github.leylaragg.letool.ratelimiter.core.RateLimiter;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;
import io.github.leylaragg.letool.tool.spel.SpelException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RateLimitAspect} 声明式限流切面测试。
 */
class RateLimitAspectTest {

    /**
     * 切面应使用方法参数上下文解析动态 key。
     */
    @Test
    void shouldResolvePolicyAndMethodArgumentKey() {
        AtomicReference<String> invocation = new AtomicReference<>();
        RateLimiter limiter = (policy, key, permits) -> {
            invocation.set(policy + "|" + key + "|" + permits);
            return RateLimitResult.allowed();
        };
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThat(proxy.send("13800138000")).isEqualTo("sent");
        assertThat(invocation).hasValue("send-sms|13800138000|1");
    }

    /**
     * 固定 key 中包含井号时不应被误判为 SpEL 表达式。
     */
    @Test
    void shouldKeepHashCharacterInFixedKey() {
        AtomicReference<String> invocation = new AtomicReference<>();
        RateLimiter limiter = (policy, key, permits) -> {
            invocation.set(key);
            return RateLimitResult.allowed();
        };
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThat(proxy.fixedHashKey()).isEqualTo("fixed");
        assertThat(invocation).hasValue("order#write");
    }

    /**
     * 请求被拒绝时应调用签名匹配的回退方法。
     */
    @Test
    void shouldInvokeFallbackWhenRequestIsRejected() {
        RateLimiter limiter = (policy, key, permits) ->
                RateLimitResult.rejected("ParamFlowException");
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThat(proxy.send("13800138000")).isEqualTo("busy:13800138000");
    }

    /**
     * SpEL 配置错误不应静默退化为原始字符串 key。
     */
    @Test
    void shouldExposeInvalidKeyExpression() {
        RateLimiter limiter = (policy, key, permits) -> RateLimitResult.allowed();
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThatThrownBy(proxy::invalidExpression)
                .isInstanceOf(SpelException.class);
    }

    /**
     * SpEL key 计算为 {@code null} 时不应静默切换为全局限流。
     */
    @Test
    void shouldRejectNullExpressionKey() {
        RateLimiter limiter = (policy, key, permits) -> RateLimitResult.allowed();
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThatThrownBy(() -> proxy.nullableKey(null))
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_002");
    }

    /**
     * SpEL key 计算为空白字符串时不应静默切换为全局限流。
     */
    @Test
    void shouldRejectBlankExpressionKey() {
        RateLimiter limiter = (policy, key, permits) -> RateLimitResult.allowed();
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThatThrownBy(() -> proxy.nullableKey(" "))
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_002");
    }

    /**
     * 固定 key 与表达式 key 同时配置时应拒绝歧义配置。
     */
    @Test
    void shouldRejectFixedKeyAndExpressionTogether() {
        RateLimiter limiter = (policy, key, permits) -> RateLimitResult.allowed();
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThatThrownBy(proxy::ambiguousKey)
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_002");
    }

    /**
     * 回退方法不存在时应抛出统一配置异常。
     */
    @Test
    void shouldRejectMissingFallbackMethod() {
        RateLimiter limiter = (policy, key, permits) ->
                RateLimitResult.rejected("FlowException");
        DemoService proxy = createProxy(new DemoService(), limiter);

        assertThatThrownBy(proxy::missingFallback)
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_003");
    }

    /**
     * 创建应用了声明式限流切面的测试代理。
     *
     * @param target  目标服务
     * @param limiter 测试限流器
     * @return 服务代理
     */
    private DemoService createProxy(DemoService target, RateLimiter limiter) {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLocalRulesEnabled(false);
        RateLimitTemplate template = new RateLimitTemplate(limiter, properties);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new RateLimitAspect(template));
        return proxyFactory.getProxy();
    }

    /**
     * 声明式限流测试服务。
     */
    static class DemoService {

        /**
         * 模拟短信发送。
         *
         * @param phone 手机号
         * @return 执行结果
         */
        @RateLimit(
                policy = "send-sms",
                keyExpression = "#phone",
                fallbackMethod = "sendFallback"
        )
        public String send(String phone) {
            return "sent";
        }

        /**
         * 模拟短信发送回退逻辑。
         *
         * @param phone 手机号
         * @return 回退结果
         */
        private String sendFallback(String phone) {
            return "busy:" + phone;
        }

        /**
         * 模拟错误的 key 表达式。
         *
         * @return 不会正常返回
         */
        @RateLimit(policy = "invalid", keyExpression = "#missing.value")
        public String invalidExpression() {
            return "invalid";
        }

        /**
         * 模拟可能计算为空值的动态 key。
         *
         * @param userId 用户标识
         * @return 执行结果
         */
        @RateLimit(policy = "nullable", keyExpression = "#userId")
        public String nullableKey(String userId) {
            return "nullable";
        }

        /**
         * 模拟包含井号的固定业务 key。
         *
         * @return 执行结果
         */
        @RateLimit(policy = "fixed", key = "order#write")
        public String fixedHashKey() {
            return "fixed";
        }

        /**
         * 模拟同时配置固定 key 和表达式 key 的错误用法。
         *
         * @return 不会正常返回
         */
        @RateLimit(policy = "ambiguous", key = "fixed", keyExpression = "#root")
        public String ambiguousKey() {
            return "ambiguous";
        }

        /**
         * 模拟缺失回退方法的配置。
         *
         * @return 不会正常返回
         */
        @RateLimit(policy = "missing", fallbackMethod = "notFound")
        public String missingFallback() {
            return "missing";
        }
    }
}
