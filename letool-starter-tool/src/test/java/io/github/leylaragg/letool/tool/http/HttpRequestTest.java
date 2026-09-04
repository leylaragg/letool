package io.github.leylaragg.letool.tool.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 HTTP 请求构建器对鉴权参数的边界约束。
 */
class HttpRequestTest {

    /** 合法 Token 应继续按原格式写入 Authorization 请求头。 */
    @Test
    void bearerTokenShouldKeepAuthorizationHeaderFormat() {
        HttpRequest.Snapshot snapshot = HttpRequest.of("http://localhost/test")
                .bearerToken("valid-token")
                .snapshot(HttpConfig.defaults());

        assertThat(snapshot.headers())
                .containsEntry("Authorization", List.of("Bearer valid-token"));
    }

    /**
     * Bearer Token 缺失时应在构建阶段立即失败，避免生成无效鉴权头。
     *
     * @param token null、空串或空白串
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void bearerTokenShouldRejectMissingToken(String token) {
        HttpRequest request = HttpRequest.of("http://localhost/test");

        assertThatThrownBy(() -> request.bearerToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("token must not be blank");
    }
}
