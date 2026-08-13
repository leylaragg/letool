package com.github.leyland.letool.rule.core;

import com.github.leyland.letool.rule.exception.RuleException;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RuleTemplate} 单元测试。
 *
 * <p>测试仅验证 Letool 薄封装的参数校验、原生响应透传和统一异常映射，
 * LiteFlow 自身的规则编排能力由集成测试覆盖。</p>
 */
@DisplayName("RuleTemplate 单元测试")
class RuleTemplateTest {

    private FlowExecutor flowExecutor;

    private RuleTemplate ruleTemplate;

    /**
     * 为每个测试准备独立的 LiteFlow 执行器和模板实例。
     */
    @BeforeEach
    void setUp() {
        flowExecutor = mock(FlowExecutor.class);
        ruleTemplate = new RuleTemplate(flowExecutor);
    }

    /**
     * 验证构造模板时立即拒绝空 LiteFlow 执行器。
     */
    @Test
    @DisplayName("FlowExecutor 为空时构造模板应立即失败")
    void shouldRejectNullFlowExecutor() {
        assertThatThrownBy(() -> new RuleTemplate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("flowExecutor must not be null");
    }

    /**
     * 验证执行成功时直接返回 LiteFlow 原生响应，不复制或改写响应内容。
     */
    @Test
    @DisplayName("执行成功时应透传 LiteFlow 原生响应")
    void shouldReturnOriginalLiteflowResponseWhenExecutionSucceeds() {
        Object requestData = new Object();
        LiteflowResponse response = successfulResponse();
        when(flowExecutor.execute2Resp("riskChain", requestData)).thenReturn(response);

        LiteflowResponse actual = ruleTemplate.execute("riskChain", requestData);

        assertThat(actual).isSameAs(response);
        verify(flowExecutor).execute2Resp("riskChain", requestData);
    }

    /**
     * 验证空规则链标识在调用 LiteFlow 前被统一拒绝。
     *
     * @param chainId 空或仅包含空白字符的规则链标识
     */
    @ParameterizedTest(name = "[{index}] chainId={0}")
    @NullSource
    @ValueSource(strings = {"", " ", " \t\r\n "})
    @DisplayName("规则链标识为空时应抛出统一异常")
    void shouldRejectBlankChainId(String chainId) {
        assertThatThrownBy(() -> ruleTemplate.execute(chainId, new Object()))
                .isInstanceOfSatisfying(RuleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RULE_001");
                    assertThat(exception.getMessage()).isEqualTo("[RULE_001] 规则链标识不能为空");
                });

        verifyNoInteractions(flowExecutor);
    }

    /**
     * 验证 LiteFlow 返回失败响应时转换为统一异常，并保留规则链及原始失败原因。
     */
    @Test
    @DisplayName("LiteFlow 返回失败响应时应映射统一异常并保留原因")
    void shouldMapFailedResponseToRuleException() {
        IllegalStateException cause = new IllegalStateException("节点执行失败");
        LiteflowResponse response = failedResponse("节点执行失败", cause);
        when(flowExecutor.execute2Resp("riskChain", "request")).thenReturn(response);

        assertThatThrownBy(() -> ruleTemplate.execute("riskChain", "request"))
                .isInstanceOfSatisfying(RuleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RULE_002");
                    assertThat(exception.getChainId()).isEqualTo("riskChain");
                    assertThat(exception.getMessage())
                            .isEqualTo("[RULE_002] 规则链执行失败：riskChain");
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    /**
     * 验证 LiteFlow 意外返回空响应时转换为统一异常，而不是泄漏空指针异常。
     */
    @Test
    @DisplayName("LiteFlow 返回空响应时应映射统一异常")
    void shouldMapNullResponseToRuleException() {
        when(flowExecutor.execute2Resp("riskChain", "request")).thenReturn(null);

        assertThatThrownBy(() -> ruleTemplate.execute("riskChain", "request"))
                .isInstanceOfSatisfying(RuleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RULE_002");
                    assertThat(exception.getChainId()).isEqualTo("riskChain");
                    assertThat(exception.getMessage())
                            .isEqualTo("[RULE_002] 规则链执行失败：riskChain");
                });
    }

    /**
     * 验证不携带原因的 LiteFlow 失败响应仍能转换为统一异常。
     */
    @Test
    @DisplayName("LiteFlow 失败响应没有原因时仍应映射统一异常")
    void shouldMapFailedResponseWithoutCauseToRuleException() {
        LiteflowResponse response = failedResponse("不应向调用方透传的内部消息", null);
        when(flowExecutor.execute2Resp("riskChain", "request")).thenReturn(response);

        assertThatThrownBy(() -> ruleTemplate.execute("riskChain", "request"))
                .isInstanceOfSatisfying(RuleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RULE_002");
                    assertThat(exception.getChainId()).isEqualTo("riskChain");
                    assertThat(exception.getMessage())
                            .isEqualTo("[RULE_002] 规则链执行失败：riskChain");
                    assertThat(exception.getCause()).isNull();
                });
    }

    /**
     * 验证调用 LiteFlow 时直接抛出的异常会被统一包装，并保留完整异常链。
     */
    @Test
    @DisplayName("LiteFlow 调用抛出异常时应映射统一异常并保留原因")
    void shouldMapThrownExceptionToRuleException() {
        IllegalArgumentException cause = new IllegalArgumentException("规则链不存在");
        when(flowExecutor.execute2Resp("missingChain", "request")).thenThrow(cause);

        assertThatThrownBy(() -> ruleTemplate.execute("missingChain", "request"))
                .isInstanceOfSatisfying(RuleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("RULE_002");
                    assertThat(exception.getChainId()).isEqualTo("missingChain");
                    assertThat(exception.getMessage())
                            .isEqualTo("[RULE_002] 规则链执行失败：missingChain");
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    /**
     * 创建成功的 LiteFlow 响应。
     *
     * @return 成功响应
     */
    private LiteflowResponse successfulResponse() {
        LiteflowResponse response = new LiteflowResponse();
        response.setSuccess(true);
        return response;
    }

    /**
     * 创建携带原始异常的 LiteFlow 失败响应。
     *
     * @param message 失败消息
     * @param cause   原始失败原因
     * @return 失败响应
     */
    private LiteflowResponse failedResponse(String message, Exception cause) {
        LiteflowResponse response = new LiteflowResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setCause(cause);
        return response;
    }
}
