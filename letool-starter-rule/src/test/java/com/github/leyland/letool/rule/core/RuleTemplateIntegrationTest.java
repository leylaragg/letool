package com.github.leyland.letool.rule.core;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleTemplate} 与 LiteFlow 的真实规则链集成测试。
 *
 * <p>测试通过 LiteFlow Spring Boot Starter 加载 XML 规则文件，并使用官方
 * {@link NodeComponent} 扩展点执行用户组件，验证 Letool 只承担调用适配职责。</p>
 */
@SpringBootTest(
        classes = RuleTemplateIntegrationTest.TestApplication.class,
        properties = {
                "liteflow.rule-source=classpath:rules/rule-template-integration.xml",
                "liteflow.print-banner=false",
                "liteflow.print-execution-log=false"
        })
@DisplayName("RuleTemplate LiteFlow 集成测试")
class RuleTemplateIntegrationTest {

    @Autowired
    private RuleTemplate ruleTemplate;

    @Autowired
    private RequestRecordingComponent requestRecordingComponent;

    /**
     * 清理上一次执行记录，避免测试之间共享可变状态。
     */
    @BeforeEach
    void resetComponent() {
        requestRecordingComponent.reset();
    }

    /**
     * 验证 XML 规则链能够调用 LiteFlow 官方组件，并把请求数据传入组件。
     */
    @Test
    @DisplayName("应通过 LiteFlow 执行真实 XML 规则链")
    void shouldExecuteRealLiteFlowChain() {
        String requestData = "risk-request";

        LiteflowResponse response = ruleTemplate.execute("ruleTemplateIntegrationChain", requestData);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getChainId()).isEqualTo("ruleTemplateIntegrationChain");
        assertThat(requestRecordingComponent.getRecordedRequest()).isEqualTo(requestData);
    }

    /**
     * 集成测试使用的最小 Spring Boot 应用。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(RequestRecordingComponent.class)
    static class TestApplication {
    }

    /**
     * 使用 LiteFlow 官方扩展点实现的用户规则组件。
     */
    @LiteflowComponent("recordRequest")
    static class RequestRecordingComponent extends NodeComponent {

        private final AtomicReference<Object> recordedRequest = new AtomicReference<>();

        /**
         * 记录 LiteFlow 传入当前规则链的请求数据。
         */
        @Override
        public void process() {
            recordedRequest.set(getRequestData());
        }

        /**
         * 获取最近一次执行记录的请求数据。
         *
         * @return 最近一次执行记录的请求数据
         */
        Object getRecordedRequest() {
            return recordedRequest.get();
        }

        /**
         * 清空执行记录。
         */
        void reset() {
            recordedRequest.set(null);
        }
    }
}
