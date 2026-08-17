package io.github.leylaragg.letool.exception.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ErrorCodeTest {

    @Test
    void factoryExposesCodeAndDefaultMessage() {
        ErrorCode errorCode = ErrorCode.of("TEST_001", "测试消息：{0}");

        assertThat(errorCode.getCode()).isEqualTo("TEST_001");
        assertThat(errorCode.getDefaultMessage()).isEqualTo("测试消息：{0}");
    }

    @Test
    void factoryRejectsBlankCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ErrorCode.of(" ", "测试消息"))
                .withMessageContaining("code");
    }

    @Test
    void factoryRejectsBlankDefaultMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ErrorCode.of("TEST_001", "\t"))
                .withMessageContaining("defaultMessage");
    }
}
