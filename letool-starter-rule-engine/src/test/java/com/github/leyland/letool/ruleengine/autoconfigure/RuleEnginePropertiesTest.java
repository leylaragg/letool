package com.github.leyland.letool.ruleengine.autoconfigure;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * 规则引擎 Spring Boot 配置属性契约测试。
 */
class RuleEnginePropertiesTest {

    /** 配置属性扫描与真实绑定使用的上下文运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesScanConfiguration.class);

    /**
     * 验证规则引擎默认开启。
     */
    @Test
    void shouldEnableRuleEngineByDefault() {
        assertThat(new RuleEngineProperties().isEnabled()).isTrue();
    }

    /**
     * 验证 starter 的九项默认限制与 core 保持一致。
     */
    @Test
    void shouldUseCoreEngineLimitDefaults() {
        EngineLimits expected = EngineLimits.defaults();
        EngineLimits actual = new RuleEngineProperties().getLimits().toEngineLimits();

        assertEveryLimit(actual, expected);
    }

    /**
     * 验证扫描得到的配置对象能绑定开关和全部限制，且映射顺序不串位。
     */
    @Test
    void shouldScanBindAndMapEveryConfiguredLimit() {
        contextRunner
                .withPropertyValues(
                        "letool.rule-engine.enabled=false",
                        "letool.rule-engine.limits.max-source-length=101",
                        "letool.rule-engine.limits.max-tokens=102",
                        "letool.rule-engine.limits.max-ast-depth=103",
                        "letool.rule-engine.limits.max-function-calls=104",
                        "letool.rule-engine.limits.max-trace-nodes=105",
                        "letool.rule-engine.limits.max-summary-length=106",
                        "letool.rule-engine.limits.max-fact-depth=107",
                        "letool.rule-engine.limits.max-fact-nodes=108",
                        "letool.rule-engine.limits.max-container-size=109")
                .run(context -> {
                    assertThat(context).hasSingleBean(RuleEngineProperties.class);
                    RuleEngineProperties properties = context.getBean(RuleEngineProperties.class);
                    EngineLimits limits = properties.getLimits().toEngineLimits();

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(limits.getMaxSourceLength()).isEqualTo(101);
                    assertThat(limits.getMaxTokens()).isEqualTo(102);
                    assertThat(limits.getMaxAstDepth()).isEqualTo(103);
                    assertThat(limits.getMaxFunctionCalls()).isEqualTo(104);
                    assertThat(limits.getMaxTraceNodes()).isEqualTo(105);
                    assertThat(limits.getMaxSummaryLength()).isEqualTo(106);
                    assertThat(limits.getMaxFactDepth()).isEqualTo(107);
                    assertThat(limits.getMaxFactNodes()).isEqualTo(108);
                    assertThat(limits.getMaxContainerSize()).isEqualTo(109);
                });
    }

    /**
     * 验证每项限制分别拒绝零和负数，并沿用 core 的稳定错误码。
     *
     * @param property 待验证的配置项名称
     * @param invalidLimit 设置单项非法值的操作
     */
    @ParameterizedTest(name = "{0} 拒绝非正数")
    @MethodSource("invalidLimits")
    void shouldRejectEveryNonPositiveLimit(
            String property,
            Consumer<RuleEngineProperties.Limits> invalidLimit) {
        RuleEngineProperties.Limits limits = new RuleEngineProperties.Limits();
        invalidLimit.accept(limits);

        assertThatThrownBy(limits::toEngineLimits)
                .as(property)
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 验证空限制对象被稳定拒绝，同时不替换原有默认对象。
     */
    @Test
    void shouldRejectNullLimitsWithoutReplacingCurrentLimits() {
        RuleEngineProperties properties = new RuleEngineProperties();
        RuleEngineProperties.Limits original = properties.getLimits();

        assertThatThrownBy(() -> properties.setLimits(null))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
        assertThat(properties.getLimits()).isSameAs(original);
    }

    /**
     * 验证补充元数据仅声明十项公开配置，名称和默认值与属性对象一致。
     *
     * @throws IOException 元数据读取或解析失败时抛出
     * @throws JSONException 元数据不符合 JSON 格式时抛出
     */
    @Test
    void shouldPublishExactConfigurationMetadata() throws IOException, JSONException {
        RuleEngineProperties defaults = new RuleEngineProperties();
        RuleEngineProperties.Limits limits = defaults.getLimits();
        Map<String, Object> expectedDefaults = Map.ofEntries(
                entry("letool.rule-engine.enabled", defaults.isEnabled()),
                entry("letool.rule-engine.limits.max-source-length", limits.getMaxSourceLength()),
                entry("letool.rule-engine.limits.max-tokens", limits.getMaxTokens()),
                entry("letool.rule-engine.limits.max-ast-depth", limits.getMaxAstDepth()),
                entry("letool.rule-engine.limits.max-function-calls", limits.getMaxFunctionCalls()),
                entry("letool.rule-engine.limits.max-trace-nodes", limits.getMaxTraceNodes()),
                entry("letool.rule-engine.limits.max-summary-length", limits.getMaxSummaryLength()),
                entry("letool.rule-engine.limits.max-fact-depth", limits.getMaxFactDepth()),
                entry("letool.rule-engine.limits.max-fact-nodes", limits.getMaxFactNodes()),
                entry("letool.rule-engine.limits.max-container-size", limits.getMaxContainerSize()));

        try (InputStream input = RuleEnginePropertiesTest.class.getResourceAsStream(
                "/META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();
            JSONObject root = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            JSONArray properties = root.getJSONArray("properties");
            Map<String, JSONObject> metadataByName = new HashMap<>();
            for (int index = 0; index < properties.length(); index++) {
                JSONObject metadata = properties.getJSONObject(index);
                assertThat(metadataByName.put(metadata.getString("name"), metadata)).isNull();
            }

            assertThat(metadataByName).hasSize(10).containsOnlyKeys(expectedDefaults.keySet());
            expectedDefaults.forEach((name, defaultValue) -> {
                JSONObject metadata = metadataByName.get(name);
                String expectedType = defaultValue instanceof Boolean
                        ? Boolean.class.getName()
                        : Integer.class.getName();

                assertThat(metadata.optString("type")).isEqualTo(expectedType);
                assertThat(metadata.opt("defaultValue")).isEqualTo(defaultValue);
                assertThat(metadata.optString("description")).isNotBlank();
            });
        }
    }

    /**
     * 逐项比较两组不可变限制。
     *
     * @param actual 实际转换结果
     * @param expected core 默认限制
     */
    private static void assertEveryLimit(EngineLimits actual, EngineLimits expected) {
        assertThat(actual.getMaxSourceLength()).isEqualTo(expected.getMaxSourceLength());
        assertThat(actual.getMaxTokens()).isEqualTo(expected.getMaxTokens());
        assertThat(actual.getMaxAstDepth()).isEqualTo(expected.getMaxAstDepth());
        assertThat(actual.getMaxFunctionCalls()).isEqualTo(expected.getMaxFunctionCalls());
        assertThat(actual.getMaxTraceNodes()).isEqualTo(expected.getMaxTraceNodes());
        assertThat(actual.getMaxSummaryLength()).isEqualTo(expected.getMaxSummaryLength());
        assertThat(actual.getMaxFactDepth()).isEqualTo(expected.getMaxFactDepth());
        assertThat(actual.getMaxFactNodes()).isEqualTo(expected.getMaxFactNodes());
        assertThat(actual.getMaxContainerSize()).isEqualTo(expected.getMaxContainerSize());
    }

    /**
     * 为九项限制分别生成零值和负值用例。
     *
     * @return 十八个单字段非法值用例
     */
    private static Stream<Arguments> invalidLimits() {
        return Stream.of(0, -1).flatMap(value -> Stream.of(
                arguments("max-source-length=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits ->
                                limits.setMaxSourceLength(value)),
                arguments("max-tokens=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits -> limits.setMaxTokens(value)),
                arguments("max-ast-depth=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits -> limits.setMaxAstDepth(value)),
                arguments("max-function-calls=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits ->
                                limits.setMaxFunctionCalls(value)),
                arguments("max-trace-nodes=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits -> limits.setMaxTraceNodes(value)),
                arguments("max-summary-length=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits ->
                                limits.setMaxSummaryLength(value)),
                arguments("max-fact-depth=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits -> limits.setMaxFactDepth(value)),
                arguments("max-fact-nodes=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits -> limits.setMaxFactNodes(value)),
                arguments("max-container-size=" + value,
                        (Consumer<RuleEngineProperties.Limits>) limits ->
                                limits.setMaxContainerSize(value))));
    }

    /**
     * 仅扫描规则引擎属性类，避免引入后续自动配置职责。
     */
    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = RuleEngineProperties.class)
    static class PropertiesScanConfiguration {
    }
}
