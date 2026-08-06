package com.github.leyland.letool.web.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Web 请求路径匹配器关键行为测试。
 */
class WebPathMatcherTest {

    /**
     * 验证路径规则按应用内路径匹配，不受 context path 影响。
     */
    @Test
    void shouldMatchApplicationPathWithoutContextPath() {
        WebPathMatcher matcher = new WebPathMatcher(List.of("/actuator/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/actuator/health");
        request.setContextPath("/demo");

        assertThat(matcher.matches(request)).isTrue();
    }

    /**
     * 验证非法路径表达式会在组件构造阶段被拒绝。
     */
    @Test
    void shouldRejectInvalidPatternAtStartup() {
        assertThatThrownBy(() -> new WebPathMatcher(List.of("/api/{id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/{id");
    }
}
