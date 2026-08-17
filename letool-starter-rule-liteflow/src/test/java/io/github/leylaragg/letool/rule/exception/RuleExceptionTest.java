package io.github.leylaragg.letool.rule.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuleException} 与 {@link RuleErrorCode} 的统一异常契约测试。
 *
 * <p>测试锁定规则模块的稳定错误码、可国际化消息参数、日志兜底消息和异常原因，
 * 避免重新维护一套与公共异常模块不兼容的异常模型。</p>
 */
@DisplayName("规则模块统一异常测试")
class RuleExceptionTest {

    /**
     * 验证规则错误码实现公共错误码契约并提供稳定定义。
     */
    @Test
    @DisplayName("规则错误码应实现公共错误码契约")
    void shouldProvideStableRuleErrorCodes() {
        assertThat(RuleErrorCode.CHAIN_ID_INVALID).isInstanceOf(ErrorCode.class);
        assertThat(RuleErrorCode.CHAIN_ID_INVALID.getCode()).isEqualTo("RULE_001");
        assertThat(RuleErrorCode.CHAIN_ID_INVALID.getDefaultMessage())
                .isEqualTo("规则链标识不能为空");

        assertThat(RuleErrorCode.EXECUTION_FAILED).isInstanceOf(ErrorCode.class);
        assertThat(RuleErrorCode.EXECUTION_FAILED.getCode()).isEqualTo("RULE_002");
        assertThat(RuleErrorCode.EXECUTION_FAILED.getDefaultMessage())
                .isEqualTo("规则链执行失败：{0}");
    }

    /**
     * 验证规则异常是不可继承的公共系统异常。
     */
    @Test
    @DisplayName("规则异常应为 final 系统异常")
    void shouldBeFinalSystemException() {
        assertThat(SystemException.class).isAssignableFrom(RuleException.class);
        assertThat(Modifier.isFinal(RuleException.class.getModifiers())).isTrue();
    }

    /**
     * 验证无效规则链标识工厂方法生成完整且稳定的异常快照。
     */
    @Test
    @DisplayName("无效规则链标识应生成 RULE_001 异常")
    void shouldCreateInvalidChainIdException() {
        RuleException exception = RuleException.invalidChainId();

        assertThat(exception.getCode()).isEqualTo("RULE_001");
        assertThat(exception.getErrorCode()).isSameAs(RuleErrorCode.CHAIN_ID_INVALID);
        assertThat(exception.getMessageArgs()).isEmpty();
        assertThat(exception.getFallbackMessage()).isEqualTo("规则链标识不能为空");
        assertThat(exception.getMessage()).isEqualTo("[RULE_001] 规则链标识不能为空");
        assertThat(exception.getChainId()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    /**
     * 验证执行失败工厂方法保留安全链标识、模板参数和底层原因。
     */
    @Test
    @DisplayName("执行失败应生成 RULE_002 异常并保留原因")
    void shouldCreateExecutionFailedException() {
        IllegalStateException cause = new IllegalStateException("内部节点异常");

        RuleException exception = RuleException.executionFailed("riskChain", cause);

        assertThat(exception.getCode()).isEqualTo("RULE_002");
        assertThat(exception.getErrorCode()).isSameAs(RuleErrorCode.EXECUTION_FAILED);
        assertThat(exception.getMessageArgs()).containsExactly("riskChain");
        assertThat(exception.getFallbackMessage()).isEqualTo("规则链执行失败：riskChain");
        assertThat(exception.getMessage()).isEqualTo("[RULE_002] 规则链执行失败：riskChain");
        assertThat(exception.getChainId()).isEqualTo("riskChain");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    /**
     * 验证 LiteFlow 未提供失败原因时仍可创建结构完整的执行失败异常。
     */
    @Test
    @DisplayName("执行失败原因为空时仍应创建统一异常")
    void shouldAllowNullExecutionFailureCause() {
        RuleException exception = RuleException.executionFailed("riskChain", null);

        assertThat(exception.getCode()).isEqualTo("RULE_002");
        assertThat(exception.getChainId()).isEqualTo("riskChain");
        assertThat(exception.getMessageArgs()).containsExactly("riskChain");
        assertThat(exception.getCause()).isNull();
    }

    /**
     * 验证执行失败工厂方法拒绝缺少有效规则链标识的调用。
     *
     * @param chainId 空或仅包含空白字符的规则链标识
     */
    @ParameterizedTest(name = "[{index}] chainId={0}")
    @NullSource
    @ValueSource(strings = {"", " ", " \t\r\n "})
    @DisplayName("执行失败工厂方法应拒绝空规则链标识")
    void shouldRejectBlankChainIdForExecutionFailure(String chainId) {
        assertThatThrownBy(() -> RuleException.executionFailed(chainId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
