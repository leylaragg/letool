package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 按宿主配置检查打印启动所需的模板、字体和临时目录。
 *
 * <p>默认不触碰外部资源；开启严格检查后，失败信息也只保留安全属性名。</p>
 *
 * @author leyland
 */
public final class PrintStartupValidator implements SmartInitializingSingleton {

    /** 启动时读取的模板仓库。 */
    private final TemplateRepository repository;

    /** 宿主交给 PDF 渲染器使用的字体。 */
    private final List<PdfFont> fonts;

    /** 决定哪些严格检查需要执行。 */
    private final PrintProperties properties;

    /**
     * 创建无请求状态的启动校验器。
     *
     * @param repository 模板仓库
     * @param fonts 已按 Spring 顺序收集的字体
     * @param properties 打印外部化配置
     */
    public PrintStartupValidator(TemplateRepository repository, List<PdfFont> fonts, PrintProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.fonts = List.copyOf(Objects.requireNonNull(fonts, "fonts 不能为空"));
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /** 根据显式开关执行启动检查，宽松配置不会访问外部资源。 */
    @Override
    public void afterSingletonsInstantiated() {
        PrintProperties.Startup startup = properties.getStartup();
        if (startup.isRequireActiveTemplate() && repository.current().isEmpty()) {
            throw failure("startup.require-active-template");
        }
        if (startup.isValidateFonts()) {
            validateFonts();
            properties.temporaryRoot().ifPresent(this::validateTemporaryRoot);
        }
    }

    /** 逐个读取少量字体头部，并及时归还宿主拥有的流。 */
    private void validateFonts() {
        for (PdfFont font : fonts) {
            try (InputStream stream = font.openStream()) {
                if (stream.readNBytes(32).length == 0) {
                    throw failure("startup.validate-fonts");
                }
            } catch (IOException | RuntimeException exception) {
                throw failure("startup.validate-fonts");
            }
        }
    }

    /**
     * 在配置目录中创建并清理一个探针文件，确认真实写入能力。
     *
     * @param temporaryRoot 宿主配置的临时根目录
     */
    private void validateTemporaryRoot(Path temporaryRoot) {
        Path probe = null;
        try {
            Files.createDirectories(temporaryRoot);
            probe = Files.createTempFile(temporaryRoot, ".letool-print-probe-", ".tmp");
        } catch (IOException | RuntimeException exception) {
            throw failure("temporary-directory");
        } finally {
            deleteProbe(probe);
        }
    }

    /**
     * 探针清理失败同样阻止严格启动，避免遗留文件被忽略。
     *
     * @param probe 本次检查创建的探针文件
     */
    private void deleteProbe(Path probe) {
        if (probe == null) {
            return;
        }
        try {
            Files.deleteIfExists(probe);
        } catch (IOException | RuntimeException exception) {
            throw failure("temporary-directory");
        }
    }

    /**
     * 创建不包含模板内容、路径或底层异常原文的启动失败。
     *
     * @param property 未通过检查的安全属性名
     * @return 可直接阻止启动的异常
     */
    private IllegalStateException failure(String property) {
        return new IllegalStateException("letool.print." + property + " 启动检查失败");
    }
}
