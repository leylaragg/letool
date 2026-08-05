package com.github.leyland.letool.mq.provider;

import com.github.leyland.letool.mq.core.MqProvider;
import com.github.leyland.letool.mq.core.MqTemplate;
import com.github.leyland.letool.mq.model.MqMessage;
import com.github.leyland.letool.mq.model.MqSendRequest;
import com.github.leyland.letool.mq.model.MqSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Letool MQ 与 Spring Cloud Stream Test Binder 的集成测试。
 */
@DisplayName("Letool MQ Test Binder 集成")
class MqSpringCloudStreamIntegrationTest {

    /**
     * 验证消息能够经过统一门面、Provider 和 Test Binder 到达真实输出目标。
     */
    @Test
    @DisplayName("应把消息发送到配置的 Binder 输出目标")
    void shouldSendMessageThroughTestBinder() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                TestChannelBinderConfiguration.getCompleteConfiguration(TestConfiguration.class))
                .run(
                        "--spring.main.web-application-type=none",
                        "--spring.jmx.enabled=false",
                        "--letool.mq.enabled=true",
                        "--spring.cloud.stream.output-bindings=order-out-0",
                        "--spring.cloud.stream.bindings.order-out-0.destination=orders")) {
            MqTemplate template = context.getBean(MqTemplate.class);
            OutputDestination outputDestination = context.getBean(OutputDestination.class);
            byte[] payload = "order-1001".getBytes(StandardCharsets.UTF_8);

            MqSendResult result = template.send(new MqSendRequest<>(
                    null,
                    "order-out-0",
                    new MqMessage<>(
                            payload,
                            Map.of("traceId", "trace-1001"),
                            "application/octet-stream")));
            Message<byte[]> outputMessage = outputDestination.receive(1_000, "orders");

            assertThat(result.provider()).isEqualTo("integration");
            assertThat(result.accepted()).isTrue();
            assertThat(outputMessage).isNotNull();
            assertThat(outputMessage.getPayload()).isEqualTo(payload);
            assertThat(outputMessage.getHeaders().get("traceId")).isEqualTo("trace-1001");
        }
    }

    /**
     * 测试 Binder 场景所需的最小应用配置。
     */
    @EnableAutoConfiguration
    static class TestConfiguration {

        /**
         * 创建绑定到 Spring Integration Test Binder 的 Provider。
         *
         * @param streamOperations Spring Cloud Stream 发送门面
         * @param bindingServiceProperties Spring Cloud Stream Binding 配置
         * @return Test Binder Provider
         */
        @Bean
        MqProvider integrationMqProvider(
                StreamOperations streamOperations,
                BindingServiceProperties bindingServiceProperties) {
            return new StreamOperationsMqProvider(
                    "integration",
                    TestChannelBinderConfiguration.NAME,
                    streamOperations,
                    bindingServiceProperties);
        }
    }
}
