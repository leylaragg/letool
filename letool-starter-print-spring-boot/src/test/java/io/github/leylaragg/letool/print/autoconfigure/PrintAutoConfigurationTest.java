package io.github.leylaragg.letool.print.autoconfigure;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.pdf.PdfDocumentRenderer;
import io.github.leylaragg.letool.print.pipeline.PrintPipelineRegistry;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.service.PrintDataAdapter;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintDefinitionRegistry;
import io.github.leylaragg.letool.print.service.PrintRuntimeSettings;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.xml.XmlPrintPipeline;
import io.github.leylaragg.letool.print.xml.XmlTemplateBinder;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationService;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompiler;
import io.github.leylaragg.letool.print.xml.XmlTemplateSetCompiler;
import io.github.leylaragg.letool.print.xml.XmlTemplateSetValidator;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.PrintFormatterRegistry;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印 Starter 默认 Bean 图和扩展退让规则测试。
 *
 * @author leyland
 */
class PrintAutoConfigurationTest {

    /** 只加载打印自动配置的轻量 Spring 上下文。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class));

    /** 没有模板、字体和业务定义时仍提供完整同步打印组件。 */
    @Test
    void shouldCreateCompleteDefaultPrintGraph() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PrintProperties.class);
            assertThat(context).hasSingleBean(PrintRuntimeSettings.class);
            assertThat(context).hasSingleBean(TemplateRepository.class);
            assertThat(context).hasSingleBean(PrintFormatterRegistry.class);
            assertThat(context).hasSingleBean(PrintExpressionRegistry.class);
            assertThat(context).hasSingleBean(PrintTagRegistry.class);
            assertThat(context).hasSingleBean(XmlTemplateCompiler.class);
            assertThat(context).hasSingleBean(XmlTemplateSetCompiler.class);
            assertThat(context).hasSingleBean(XmlTemplateCompilationCache.class);
            assertThat(context).hasSingleBean(XmlTemplateSetValidator.class);
            assertThat(context).hasSingleBean(TemplateSetPublisher.class);
            assertThat(context).hasSingleBean(XmlTemplateCompilationService.class);
            assertThat(context).hasSingleBean(XmlTemplateBinder.class);
            assertThat(context).hasSingleBean(PdfDocumentRenderer.class);
            assertThat(context).hasSingleBean(DocumentRendererRegistry.class);
            assertThat(context).hasSingleBean(XmlPrintPipeline.class);
            assertThat(context).hasSingleBean(PrintPipelineRegistry.class);
            assertThat(context).hasSingleBean(PrintEngine.class);
            assertThat(context).hasSingleBean(PrintDefinitionRegistry.class);
            assertThat(context).hasSingleBean(PrintService.class);
            assertThat(context.getBean(PrintDefinitionRegistry.class).registeredCodes()).isEmpty();
        });
    }

    /** 总开关关闭后不留下属性、注册表或业务门面。 */
    @Test
    void shouldCreateNoPrintBeansWhenDisabled() {
        contextRunner.withPropertyValues("letool.print.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PrintProperties.class);
                    assertThat(context).doesNotHaveBean(PrintEngine.class);
                    assertThat(context).doesNotHaveBean(PrintService.class);
                });
    }

    /** 宿主提供基础仓库时，所有默认链路统一使用该实例。 */
    @Test
    void shouldBackOffForCustomTemplateRepository() {
        contextRunner.withUserConfiguration(CustomRepositoryConfiguration.class)
                .run(context -> {
                    TemplateRepository repository = context.getBean(TemplateRepository.class);
                    assertThat(repository).isSameAs(
                            context.getBean(CustomRepositoryConfiguration.class).repository);
                    assertThat(context).hasSingleBean(TemplateRepository.class);
                });
    }

    /** 重复业务定义在注册表构造阶段使上下文启动失败。 */
    @Test
    void shouldFailStartupForDuplicateBusinessDefinition() {
        contextRunner.withUserConfiguration(DuplicateDefinitionConfiguration.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause()
                        .hasMessageContaining("invoice"));
    }

    /** 声明一个可识别的宿主模板仓库。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomRepositoryConfiguration {

        /** 测试需要确认默认装配没有替换的仓库。 */
        private final TemplateRepository repository = new InMemoryTemplateRepository();

        /** @return 宿主提供的模板仓库 */
        @Bean
        TemplateRepository templateRepository() {
            return repository;
        }
    }

    /** 提供两个相同编码定义以触发启动期治理。 */
    @Configuration(proxyBeanMethods = false)
    static class DuplicateDefinitionConfiguration {

        /** @return 第一条业务定义 */
        @Bean
        PrintDefinition<Long> firstDefinition() {
            return definition("first-template");
        }

        /** @return 编码相同的第二条业务定义 */
        @Bean
        PrintDefinition<Long> secondDefinition() {
            return definition("second-template");
        }

        /** 创建重复编码但模板代码不同的业务定义。 */
        private PrintDefinition<Long> definition(String templateCode) {
            PrintDataAdapter<Long> adapter = request -> PrintContext.of(
                    1, JsonNodeFactory.instance.objectNode().put("id", request));
            return PrintDefinition.of("invoice", templateCode, Long.class, adapter);
        }
    }
}
