package com.github.leyland.letool.excel.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExcelAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定 Excel 工具 starter 的 classpath 边界：有 EasyExcel 时加载，缺少 EasyExcel 时安静退让。</p>
 */
class ExcelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));

    /**
     * 验证 EasyExcel 在 classpath 中时自动配置可以正常加载。
     */
    @Test
    void shouldLoadWhenEasyExcelIsPresent() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(ExcelAutoConfiguration.class));
    }

    /**
     * 验证缺少 EasyExcel 时自动配置不会加载，避免工具模块误启动失败。
     */
    @Test
    void shouldBackOffWhenEasyExcelIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("com.alibaba.excel"))
                .run(context ->
                        assertThat(context).doesNotHaveBean(ExcelAutoConfiguration.class));
    }
}
