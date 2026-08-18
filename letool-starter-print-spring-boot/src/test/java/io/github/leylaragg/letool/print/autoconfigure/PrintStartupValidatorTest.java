package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打印 Starter 严格启动检查测试。
 *
 * @author leyland
 */
class PrintStartupValidatorTest {

    /** 临时目录只用于验证探针文件的创建和清理。 */
    @TempDir
    private Path temporaryDirectory;

    /** 默认配置保持宽松，没有模板或字体也能启动。 */
    @Test
    void shouldAllowEmptyInfrastructureByDefault() {
        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(), new PrintProperties());

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    /** 宿主要求活动模板时，空仓库应在启动阶段被明确拒绝。 */
    @Test
    void shouldRejectMissingActiveTemplateWhenRequired() {
        PrintProperties properties = new PrintProperties();
        properties.getStartup().setRequireActiveTemplate(true);

        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-active-template");
    }

    /** 开启字体校验后，每个字体流都会被打开并由校验器关闭。 */
    @Test
    void shouldValidateConfiguredFontsAndTemporaryDirectory() {
        TrackingInputStream fontStream = new TrackingInputStream(new byte[]{1, 2, 3});
        PdfFont font = new PdfFont("Test Font", () -> fontStream, false);
        PrintProperties properties = strictInfrastructureProperties(temporaryDirectory);

        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(font), properties);

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
        assertThat(fontStream.closed).isTrue();
    }

    /** 空字体流无法证明字体资源可用，应按安全配置错误拒绝。 */
    @Test
    void shouldRejectEmptyFontStream() {
        PdfFont font = new PdfFont(
                "Empty Font", () -> new ByteArrayInputStream(new byte[0]), false);
        PrintProperties properties = strictInfrastructureProperties(temporaryDirectory);

        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(font), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validate-fonts");
    }

    /** 字体供应器的异常原文不能进入启动失败信息。 */
    @Test
    void shouldHideFontProviderFailureDetails() {
        PdfFont font = new PdfFont("Broken Font", () -> {
            throw new IllegalStateException("secret-font-path");
        }, false);
        PrintProperties properties = strictInfrastructureProperties(temporaryDirectory);

        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(font), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validate-fonts")
                .hasMessageNotContaining("secret-font-path");
    }

    /** 字体供应器返回空值时也按资源不可用处理。 */
    @Test
    void shouldRejectNullFontStream() {
        PdfFont font = new PdfFont("Null Font", () -> null, false);
        PrintProperties properties = strictInfrastructureProperties(temporaryDirectory);
        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(font), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validate-fonts");
    }

    /** 探针目录不可创建时只报告安全属性名。 */
    @Test
    void shouldHideTemporaryDirectoryWhenProbeCannotBeCreated() throws Exception {
        Path blocker = Files.createFile(temporaryDirectory.resolve("blocker"));
        PrintProperties properties = strictInfrastructureProperties(blocker.resolve("child"));

        PrintStartupValidator validator = validator(new InMemoryTemplateRepository(), List.of(), properties);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporary-directory")
                .hasMessageNotContaining(blocker.toString());
    }

    /** 创建开启基础设施检查的测试配置。 */
    private PrintProperties strictInfrastructureProperties(Path temporaryRoot) {
        PrintProperties properties = new PrintProperties();
        properties.getStartup().setValidateFonts(true);
        properties.setTemporaryDirectory(temporaryRoot.toString());
        return properties;
    }

    /** 组装只依赖公开基础设施契约的启动校验器。 */
    private PrintStartupValidator validator(TemplateRepository repository, List<PdfFont> fonts,
                                            PrintProperties properties) {
        return new PrintStartupValidator(repository, fonts, properties);
    }

    /** 记录字体流是否由启动检查正确关闭。 */
    private static final class TrackingInputStream extends ByteArrayInputStream {

        /** 流关闭后置为 {@code true}。 */
        private boolean closed;

        /** 创建包含少量字体头部数据的测试流。 */
        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
