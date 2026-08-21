package io.github.leylaragg.letool.print.autoconfigure;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.TextNode;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.pdf.OpenHtmlPdfRenderer;
import io.github.leylaragg.letool.print.pdf.PdfRenderer;
import io.github.leylaragg.letool.print.pipeline.PrintPipelineRegistry;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.render.OutputCapability;
import io.github.leylaragg.letool.print.service.PrintDataAdapter;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintDefinitionRegistry;
import io.github.leylaragg.letool.print.service.PrintRuntimeSettings;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateSource;
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

import java.util.Optional;
import java.util.Set;

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
            assertThat(context).hasSingleBean(PdfRenderer.class);
            assertThat(context).hasSingleBean(OpenHtmlPdfRenderer.class);
            assertThat(context).hasSingleBean(DocumentRendererRegistry.class);
            assertThat(context).hasSingleBean(XmlPrintPipeline.class);
            assertThat(context).hasSingleBean(PrintPipelineRegistry.class);
            assertThat(context).hasSingleBean(PrintEngine.class);
            assertThat(context).hasSingleBean(PrintDefinitionRegistry.class);
            assertThat(context).hasSingleBean(PrintService.class);
            assertThat(context).hasSingleBean(PrintStartupValidator.class);
            assertThat(context.getBean(PrintDefinitionRegistry.class).registeredCodes()).isEmpty();
        });
    }

    /** 严格模板检查通过真实自动配置链路阻止空仓库启动。 */
    @Test
    void shouldFailStartupWhenActiveTemplateIsRequiredButMissing() {
        contextRunner
                .withPropertyValues("letool.print.startup.require-active-template=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("require-active-template");
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

    /** 只读模板来源足以支撑打印，默认可写仓库和发布器都会退让。 */
    @Test
    void shouldUseReadOnlySourceWithoutCreatingPublisher() {
        contextRunner.withUserConfiguration(ReadOnlySourceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TemplateSource.class);
                    assertThat(context).doesNotHaveBean(TemplateRepository.class);
                    assertThat(context).doesNotHaveBean(TemplateSetPublisher.class);
                    assertThat(context).hasSingleBean(PrintService.class);
                });
    }

    /** 宿主实现公开 PDF SPI 后，默认 OpenHTML 渲染器不再参与装配。 */
    @Test
    void shouldBackOffDefaultPdfForCustomRenderer() {
        contextRunner.withUserConfiguration(CustomPdfRendererConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PdfRenderer.class);
                    assertThat(context).doesNotHaveBean(OpenHtmlPdfRenderer.class);
                });
    }

    /** 两个 PDF 实现语义不明确，注册表应让应用在启动阶段失败。 */
    @Test
    void shouldFailStartupForDuplicatePdfRenderers() {
        contextRunner.withUserConfiguration(DuplicatePdfRendererConfiguration.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(PrintPipelineException.class)
                        .rootCause()
                        .hasMessageContaining("pdf"));
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

    /** 提供只读来源，模拟远程或数据库查询适配器。 */
    @Configuration(proxyBeanMethods = false)
    static class ReadOnlySourceConfiguration {

        /** @return 当前没有模板的只读来源 */
        @Bean
        TemplateSource templateSource() {
            return new TemplateSource() {
                /** 指定版本同样交给外部来源，当前探针保持空结果。 */
                @Override
                public Optional<TemplateSet> find(long version) {
                    return Optional.empty();
                }

                /** 当前版本尚未由外部系统准备。 */
                @Override
                public Optional<TemplateSet> current() {
                    return Optional.empty();
                }
            };
        }
    }

    /** 提供宿主自定义的 PDF 输出实现。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomPdfRendererConfiguration {

        /** @return 用于验证默认实现退让的 PDF 渲染器 */
        @Bean
        PdfRenderer customPdfRenderer() {
            return new TestPdfRenderer();
        }
    }

    /** 同时声明两个 PDF 实现，验证注册表仍保留唯一性约束。 */
    @Configuration(proxyBeanMethods = false)
    static class DuplicatePdfRendererConfiguration {

        /** @return 第一个 PDF 实现 */
        @Bean
        PdfRenderer firstPdfRenderer() {
            return new TestPdfRenderer();
        }

        /** @return 输出格式相同的第二个 PDF 实现 */
        @Bean
        PdfRenderer secondPdfRenderer() {
            return new TestPdfRenderer();
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

    /** 只用于检查 Spring 装配边界的最小 PDF 实现。 */
    private static final class TestPdfRenderer implements PdfRenderer {

        /** 当前探针只声明一个基础节点。 */
        private final OutputCapability capability = new OutputCapability(Set.of(TextNode.class));

        /** @return PDF 输出格式 */
        @Override
        public OutputFormat outputFormat() {
            return OutputFormat.PDF;
        }

        /** @return 当前探针声明的最小能力 */
        @Override
        public OutputCapability capability() {
            return capability;
        }

        /** 装配测试不会进入真实渲染。 */
        @Override
        public PrintResult render(
                DocumentModel document, RenderOptions options, PrintOutput output) {
            throw new UnsupportedOperationException("测试不会执行渲染");
        }
    }
}
