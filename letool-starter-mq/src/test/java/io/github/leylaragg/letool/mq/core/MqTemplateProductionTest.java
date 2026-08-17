package io.github.leylaragg.letool.mq.core;

import io.github.leylaragg.letool.mq.exception.MqException;
import io.github.leylaragg.letool.mq.model.MqMessage;
import io.github.leylaragg.letool.mq.model.MqSendRequest;
import io.github.leylaragg.letool.mq.model.MqSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MqTemplate} 生产路由行为测试。
 */
@DisplayName("MqTemplate 生产路由行为")
class MqTemplateProductionTest {

    /**
     * 验证单个 Provider 无需额外默认配置即可使用。
     */
    @Test
    @DisplayName("单 Provider 应自动成为默认项")
    void singleProviderShouldBeSelectedAutomatically() {
        RecordingProvider rabbit = new RecordingProvider("rabbit");
        MqTemplate template = new MqTemplate(List.of(rabbit), null);

        MqSendResult result = template.send("order-out-0", "payload");

        assertThat(result.provider()).isEqualTo("rabbit");
        assertThat(rabbit.requests).hasSize(1);
        assertThat(rabbit.requests.get(0).message().payload()).isEqualTo("payload");
    }

    /**
     * 验证多 Provider 使用配置的默认项。
     */
    @Test
    @DisplayName("多 Provider 应使用配置的默认项")
    void multipleProvidersShouldUseConfiguredDefault() {
        RecordingProvider rabbit = new RecordingProvider("rabbit");
        RecordingProvider kafka = new RecordingProvider("kafka");
        MqTemplate template = new MqTemplate(List.of(rabbit, kafka), " Kafka ");

        MqSendResult result = template.send("order-out-0", "payload");

        assertThat(result.provider()).isEqualTo("kafka");
        assertThat(rabbit.requests).isEmpty();
        assertThat(kafka.requests).hasSize(1);
    }

    /**
     * 验证调用方可以显式选择 Provider。
     */
    @Test
    @DisplayName("显式 Provider 应覆盖默认项")
    void explicitProviderShouldOverrideDefault() {
        RecordingProvider rabbit = new RecordingProvider("rabbit");
        RecordingProvider kafka = new RecordingProvider("kafka");
        MqTemplate template = new MqTemplate(List.of(rabbit, kafka), "rabbit");

        MqSendResult result = template.send(" KAFKA ", "order-out-0", "payload");

        assertThat(result.provider()).isEqualTo("kafka");
        assertThat(kafka.requests).singleElement()
                .extracting(MqSendRequest::provider)
                .isEqualTo("kafka");
    }

    /**
     * 验证完整请求会原样传递给目标 Provider。
     */
    @Test
    @DisplayName("完整请求应传递给目标 Provider")
    void completeRequestShouldBeDelegated() {
        RecordingProvider rabbit = new RecordingProvider("rabbit");
        MqTemplate template = new MqTemplate(List.of(rabbit), null);
        MqSendRequest<String> request = new MqSendRequest<>(
                null,
                "order-out-0",
                new MqMessage<>("payload", java.util.Map.of("traceId", "trace-1"), "application/json"));

        template.send(request);

        assertThat(rabbit.requests).containsExactly(request);
    }

    /**
     * 验证未知 Provider 使用稳定错误码。
     */
    @Test
    @DisplayName("未知 Provider 应快速失败")
    void unknownProviderShouldFailFast() {
        MqTemplate template = new MqTemplate(List.of(new RecordingProvider("rabbit")), null);

        assertThatThrownBy(() -> template.send("kafka", "order-out-0", "payload"))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_002"));
    }

    /**
     * 验证 Provider 名称大小写不敏感且不能重复。
     */
    @Test
    @DisplayName("重复 Provider 名称应在构造时失败")
    void duplicateProviderNamesShouldFailAtConstruction() {
        assertThatThrownBy(() -> new MqTemplate(
                List.of(new RecordingProvider("rabbit"), new RecordingProvider(" RABBIT ")),
                "rabbit"))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_003"));
    }

    /**
     * 验证多 Provider 未指定默认项时拒绝启动。
     */
    @Test
    @DisplayName("多 Provider 无默认项应在构造时失败")
    void multipleProvidersWithoutDefaultShouldFailAtConstruction() {
        assertThatThrownBy(() -> new MqTemplate(
                List.of(new RecordingProvider("rabbit"), new RecordingProvider("kafka")),
                null))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_001"));
    }

    /**
     * 验证默认项必须对应已经注册的 Provider。
     */
    @Test
    @DisplayName("不存在的默认 Provider 应在构造时失败")
    void missingDefaultProviderShouldFailAtConstruction() {
        assertThatThrownBy(() -> new MqTemplate(
                List.of(new RecordingProvider("rabbit")),
                "kafka"))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_001"));
    }

    /**
     * 验证空 Provider 集合不能创建可用门面。
     */
    @Test
    @DisplayName("空 Provider 集合应在构造时失败")
    void emptyProvidersShouldFailAtConstruction() {
        assertThatThrownBy(() -> new MqTemplate(List.of(), null))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_001"));
    }

    /**
     * 验证公共入口不接受空请求。
     */
    @Test
    @DisplayName("空发送请求应拒绝")
    void nullRequestShouldBeRejected() {
        MqTemplate template = new MqTemplate(List.of(new RecordingProvider("rabbit")), null);

        assertThatThrownBy(() -> template.send((MqSendRequest<?>) null))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_004"));
    }

    /**
     * 验证用户 Provider 返回空结果时不会被当作成功。
     */
    @Test
    @DisplayName("Provider 返回空结果应转换为扩展执行异常")
    void nullProviderResultShouldBeRejected() {
        MqTemplate template = new MqTemplate(List.of(new NullResultProvider()), null);

        assertThatThrownBy(() -> template.send("order-out-0", "payload"))
                .isInstanceOfSatisfying(MqException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_008"));
    }

    /**
     * 验证用户 Provider 的非结构化异常会保留原因链。
     */
    @Test
    @DisplayName("Provider 运行时异常应转换并保留原因链")
    void providerRuntimeFailureShouldPreserveCause() {
        MqTemplate template = new MqTemplate(List.of(new FailingProvider()), null);

        assertThatThrownBy(() -> template.send("order-out-0", "payload"))
                .isInstanceOfSatisfying(MqException.class, exception -> {
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("MQ_008");
                    assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }

    /**
     * 记录收到请求的测试 Provider。
     */
    private static final class RecordingProvider implements MqProvider {

        private final String name;
        private final List<MqSendRequest<?>> requests = new ArrayList<>();

        /**
         * 创建记录型 Provider。
         *
         * @param name Provider 名称
         */
        private RecordingProvider(String name) {
            this.name = name;
        }

        /**
         * 返回 Provider 名称。
         *
         * @return Provider 名称
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * 记录发送请求并返回接受结果。
         *
         * @param request 发送请求
         * @return 接受结果
         */
        @Override
        public MqSendResult send(MqSendRequest<?> request) {
            requests.add(request);
            return new MqSendResult(name, request.bindingName(), true, Instant.now());
        }
    }

    /**
     * 模拟错误返回空结果的用户 Provider。
     */
    private static final class NullResultProvider implements MqProvider {

        /**
         * 返回 Provider 名称。
         *
         * @return Provider 名称
         */
        @Override
        public String name() {
            return "null-result";
        }

        /**
         * 返回空结果以验证门面防御。
         *
         * @param request 发送请求
         * @return 固定返回 {@code null}
         */
        @Override
        public MqSendResult send(MqSendRequest<?> request) {
            return null;
        }
    }

    /**
     * 模拟抛出非结构化异常的用户 Provider。
     */
    private static final class FailingProvider implements MqProvider {

        /**
         * 返回 Provider 名称。
         *
         * @return Provider 名称
         */
        @Override
        public String name() {
            return "failing";
        }

        /**
         * 抛出固定异常以验证原因链保留。
         *
         * @param request 发送请求
         * @return 不会返回
         */
        @Override
        public MqSendResult send(MqSendRequest<?> request) {
            throw new IllegalStateException("provider failed");
        }
    }
}
