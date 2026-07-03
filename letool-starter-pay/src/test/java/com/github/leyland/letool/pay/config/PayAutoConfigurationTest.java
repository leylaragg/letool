package com.github.leyland.letool.pay.config;

import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.core.PayTemplate;
import com.github.leyland.letool.pay.model.PayOrder;
import com.github.leyland.letool.pay.model.PayResult;
import com.github.leyland.letool.pay.model.RefundOrder;
import com.github.leyland.letool.pay.provider.AlipayProvider;
import com.github.leyland.letool.pay.provider.MockPayProvider;
import com.github.leyland.letool.pay.provider.WechatPayProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PayAutoConfiguration} 的自动装配契约测试。
 *
 * <p>支付模块当前只内置 mock/stub provider。测试要求这些 provider 必须显式开启，
 * 避免模拟支付、模拟退款和模拟验签被误用于真实资金链路。</p>
 */
class PayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PayAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证支付模块默认保持未启用状态。
     */
    @Test
    void shouldStayInactiveByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PayProvider.class);
            assertThat(context).doesNotHaveBean(PayTemplate.class);
        });
    }

    /**
     * 验证启用支付模块但没有显式 stub 或自定义 provider 时会 fail-fast。
     */
    @Test
    void shouldFailFastWhenEnabledWithoutStubModeOrCustomProvider() {
        contextRunner
                .withPropertyValues("letool.pay.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("未注册任何 PayProvider")
                            .hasMessageContaining("stub-enabled");
                });
    }

    /**
     * 验证显式开启 stub 模式后才会创建内置支付 provider。
     */
    @Test
    void shouldCreateStubProvidersWhenStubModeIsExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "letool.pay.enabled=true",
                        "letool.pay.stub-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AlipayProvider.class);
                    assertThat(context).hasSingleBean(WechatPayProvider.class);
                    assertThat(context).hasSingleBean(MockPayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                    assertThat(context.getBean(PayTemplate.class).getProviderCount()).isEqualTo(3);
                });
    }

    /**
     * 验证业务项目注册真实支付 provider 时，自动配置只组装模板，不创建内置 stub。
     */
    @Test
    void shouldBuildTemplateFromUserPayProviders() {
        contextRunner
                .withPropertyValues("letool.pay.enabled=true")
                .withUserConfiguration(UserPayConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PayProvider.class);
                    assertThat(context).doesNotHaveBean(AlipayProvider.class);
                    assertThat(context).doesNotHaveBean(WechatPayProvider.class);
                    assertThat(context).doesNotHaveBean(MockPayProvider.class);
                    assertThat(context).hasSingleBean(PayTemplate.class);
                    assertThat(context.getBean(PayTemplate.class).getProviderCount()).isEqualTo(1);
                });
    }

    /**
     * 模拟业务项目自行接入真实支付 SDK 的 provider。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserPayConfiguration {

        @Bean
        PayProvider payProvider() {
            return new TestPayProvider();
        }
    }

    /**
     * 用于自动装配测试的支付 provider，不访问真实支付平台。
     */
    static class TestPayProvider implements PayProvider {

        @Override
        public PayResult pay(PayOrder order) {
            return PayResult.success(order.getOutTradeNo(), "test-transaction", order.getTotalAmount());
        }

        @Override
        public PayResult query(String outTradeNo) {
            return PayResult.success(outTradeNo, "test-transaction", BigDecimal.ZERO);
        }

        @Override
        public PayResult refund(RefundOrder refundOrder) {
            return PayResult.success(refundOrder.getOutTradeNo(), "test-refund", refundOrder.getRefundAmount());
        }

        @Override
        public PayResult queryRefund(String refundNo) {
            return PayResult.success(refundNo, "test-refund", BigDecimal.ZERO);
        }

        @Override
        public boolean verifySign(Map<String, String> params, String sign) {
            return true;
        }

        @Override
        public String getProviderName() {
            return "ALIPAY";
        }
    }
}
