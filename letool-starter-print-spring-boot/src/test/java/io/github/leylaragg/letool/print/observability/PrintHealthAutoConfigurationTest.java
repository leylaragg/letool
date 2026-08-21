package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.autoconfigure.PrintAutoConfiguration;
import io.github.leylaragg.letool.print.autoconfigure.PrintHealthAutoConfiguration;
import io.github.leylaragg.letool.print.autoconfigure.PrintProperties;
import io.github.leylaragg.letool.print.autoconfigure.PrintSpelAutoConfiguration;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 打印健康检查的条件装配和脱敏测试。
 *
 * @author leyland
 */
class PrintHealthAutoConfigurationTest {

    /** 隔离需要真实探测目录的测试。 */
    @TempDir
    private Path temporaryDirectory;

    /** 加载打印主链路和健康自动配置。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PrintSpelAutoConfiguration.class,
                    PrintAutoConfiguration.class,
                    PrintHealthAutoConfiguration.class));

    /** 默认空仓库是可用状态，并明确标记尚未激活模板。 */
    @Test
    void shouldExposeHealthyDefaultInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PrintService.class);
            assertThat(context).hasSingleBean(PrintTemplateHealthIndicator.class);
            assertThat(context).hasSingleBean(PrintInfrastructureHealthIndicator.class);

            Health templateHealth = context.getBean(PrintTemplateHealthIndicator.class).health();
            assertThat(templateHealth.getStatus()).isEqualTo(Status.UP);
            assertThat(templateHealth.getDetails()).containsEntry("active", false);
            assertThat(context.getBean(PrintInfrastructureHealthIndicator.class)
                    .health().getStatus()).isEqualTo(Status.UP);
        });
    }

    /** 已激活集合只暴露版本和摘要，不输出模板正文。 */
    @Test
    void shouldExposeSafeActiveTemplateDetails() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        new TemplateSetPublisher(repository, List.of())
                .publishAndActivate(7, List.of(template(7)));

        Health health = new PrintTemplateHealthIndicator(repository, new PrintProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("active", true)
                .containsEntry("version", 7L)
                .containsKey("digest");
        assertThat(health.toString()).doesNotContain("secret-template-body");
    }

    /** 仓储异常只产生稳定 DOWN 状态，不回显底层消息。 */
    @Test
    void shouldHideRepositoryFailureDetails() {
        TemplateRepository repository = new FailingTemplateRepository();

        Health health = new PrintTemplateHealthIndicator(repository, new PrintProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.toString()).doesNotContain("secret-repository-message");
    }

    /** 字体供应器异常不会进入健康详情。 */
    @Test
    void shouldHideFontFailureDetails() {
        PdfFont font = new PdfFont("Broken Font", FontWeight.NORMAL, () -> {
            throw new IllegalStateException("secret-font-location");
        }, false);

        Health health = new PrintInfrastructureHealthIndicator(List.of(font), new PrintProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.toString()).doesNotContain("secret-font-location");
    }

    /** 严格模式下，尚未激活模板的仓库应报告不可用。 */
    @Test
    void shouldReportMissingActiveTemplateInStrictMode() {
        PrintProperties properties = new PrintProperties();
        properties.getStartup().setRequireActiveTemplate(true);

        Health health = new PrintTemplateHealthIndicator(new InMemoryTemplateRepository(), properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("active", false);
    }

    /** 临时目录无法写入时只报告组件名，不泄露宿主路径。 */
    @Test
    void shouldHideUnavailableTemporaryDirectory() throws Exception {
        Path blocker = Files.createFile(temporaryDirectory.resolve("blocker"));
        PrintProperties properties = new PrintProperties();
        properties.setTemporaryDirectory(blocker.resolve("child").toString());

        Health health = new PrintInfrastructureHealthIndicator(List.of(), properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("component", "temporary-directory");
        assertThat(health.toString()).doesNotContain(blocker.toString());
    }

    /** 显式关闭健康检查时不注册对应组件。 */
    @Test
    void shouldBackOffWhenHealthIsDisabled() {
        contextRunner.withPropertyValues("letool.print.health.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(PrintTemplateHealthIndicator.class));
    }

    /** Actuator 不在运行时类路径时，主打印链路仍可启动。 */
    @Test
    void shouldStartWithoutActuatorClasses() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .run(context -> {
                    assertThat(context).hasSingleBean(PrintService.class);
                    assertThat(context).doesNotHaveBean(PrintTemplateHealthIndicator.class);
                });
    }

    /** 宿主只接管打印门面时，不完整的默认健康检查应主动退让。 */
    @Test
    void shouldBackOffWhenHealthDependenciesAreMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PrintHealthAutoConfiguration.class))
                .withUserConfiguration(CustomPrintServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PrintTemplateHealthIndicator.class);
                    assertThat(context).doesNotHaveBean(PrintInfrastructureHealthIndicator.class);
                });
    }

    /** 只有打印配置时仍可检查 PDF 基础设施，不强求模板仓库存在。 */
    @Test
    void shouldCreateInfrastructureHealthWithoutTemplateRepository() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PrintHealthAutoConfiguration.class))
                .withUserConfiguration(CustomPrintServiceConfiguration.class, PrintPropertiesConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PrintTemplateHealthIndicator.class);
                    assertThat(context).hasSingleBean(PrintInfrastructureHealthIndicator.class);
                });
    }

    /** 创建健康详情中不能出现正文的文档模板。 */
    private TemplateDefinition template(long version) {
        PrintTemplate template = new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, 1, version, 1,
                "secret-template-body".getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }

    /** 模拟宿主完全接管打印门面的最小配置。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomPrintServiceConfiguration {

        /** @return 不依赖默认打印基础设施的门面 */
        @Bean
        PrintService printService() {
            return mock(PrintService.class);
        }
    }

    /** 单独提供基础设施健康检查所需的外部化配置。 */
    @Configuration(proxyBeanMethods = false)
    static class PrintPropertiesConfiguration {

        /** @return 使用默认宽松设置的打印配置 */
        @Bean
        PrintProperties printProperties() {
            return new PrintProperties();
        }
    }

    /** 只在读取活动模板时模拟底层仓储故障。 */
    private static final class FailingTemplateRepository implements TemplateRepository {

        @Override
        public Optional<TemplateSet> find(long version) {
            return Optional.empty();
        }

        @Override
        public Optional<TemplateSet> current() {
            throw new IllegalStateException("secret-repository-message");
        }

        @Override
        public TemplateSet publish(TemplateSet templateSet) {
            return templateSet;
        }

        @Override
        public TemplateSet publishAndActivate(TemplateSet templateSet) {
            return templateSet;
        }

        @Override
        public TemplateSet activate(long version) {
            throw new UnsupportedOperationException();
        }
    }
}
