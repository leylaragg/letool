package io.github.leylaragg.letool.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Web 模块配置模型关键行为测试。
 */
class WebPropertiesTest {

    /**
     * 验证无需配置时采用低成本且可直接使用的生产默认值。
     */
    @Test
    void shouldUseSafeProductionDefaults() {
        WebProperties properties = new WebProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getResponseWrapper().isEnabled()).isTrue();
        assertThat(properties.getApiVersion().isEnabled()).isTrue();
        assertThat(properties.getApiVersion().getHeaderName()).isEqualTo("X-API-Version");
        assertThat(properties.getApiVersion().getParameterName()).isEqualTo("apiVersion");
        assertThat(properties.getRepeatableRequest().isEnabled()).isFalse();
        assertThat(properties.getRepeatableRequest().getMaxBodySize())
                .isEqualTo(DataSize.ofMegabytes(1));
        assertThat(properties.getRepeatableRequest().getIncludeMediaTypes())
                .containsExactly(
                        "application/json",
                        "application/*+json",
                        "application/xml",
                        "application/*+xml",
                        "text/*");
    }

    /**
     * 验证列表配置不会保留调用方的可变引用，也不会向外暴露可变集合。
     */
    @Test
    void shouldDefensivelyCopyConfiguredLists() {
        List<String> paths = new ArrayList<>(List.of("/actuator/**"));
        WebProperties.ResponseWrapper responseWrapper = new WebProperties.ResponseWrapper();

        responseWrapper.setExcludePaths(paths);
        paths.add("/changed/**");

        assertThat(responseWrapper.getExcludePaths()).containsExactly("/actuator/**");
        assertThatThrownBy(() -> responseWrapper.getExcludePaths().add("/other/**"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证嵌套配置不能被替换为 {@code null}，避免自动配置阶段出现延迟空指针。
     */
    @Test
    void shouldRejectNullNestedConfiguration() {
        WebProperties properties = new WebProperties();

        assertThatThrownBy(() -> properties.setApiVersion(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("apiVersion");
        assertThatThrownBy(() -> properties.setRepeatableRequest(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repeatableRequest");
    }
}
