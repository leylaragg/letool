package io.github.leylaragg.letool.sensitive.core;

import io.github.leylaragg.letool.sensitive.exception.SensitiveErrorCode;
import io.github.leylaragg.letool.sensitive.exception.SensitiveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脱敏上下文的不可变性与边界测试。
 */
@DisplayName("脱敏上下文关键行为")
class MaskContextTest {

    /**
     * 验证链式派生返回新对象，不会修改共享的默认上下文。
     */
    @Test
    @DisplayName("链式派生不应污染默认上下文")
    void shouldNotMutateDefaultContext() {
        MaskContext customized = MaskContext.DEFAULT
                .withKeepPrefix(2)
                .withKeepSuffix(2)
                .withMaskChar('#');

        assertThat(customized).isNotSameAs(MaskContext.DEFAULT);
        assertThat(customized.getKeepPrefix()).isEqualTo(2);
        assertThat(customized.getKeepSuffix()).isEqualTo(2);
        assertThat(customized.getMaskChar()).isEqualTo('#');
        assertThat(MaskContext.DEFAULT.getKeepPrefix()).isEqualTo(-1);
        assertThat(MaskContext.DEFAULT.getKeepSuffix()).isEqualTo(-1);
        assertThat(MaskContext.DEFAULT.getMaskChar()).isEqualTo('*');
    }

    /**
     * 验证非法保留长度在进入策略前即被拒绝。
     */
    @Test
    @DisplayName("非法保留长度应抛出结构化配置异常")
    void shouldRejectInvalidKeepLength() {
        assertThatThrownBy(() -> MaskContext.DEFAULT.withKeepPrefix(-2))
                .isInstanceOfSatisfying(SensitiveException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(SensitiveErrorCode.CONFIGURATION_INVALID));
    }
}
