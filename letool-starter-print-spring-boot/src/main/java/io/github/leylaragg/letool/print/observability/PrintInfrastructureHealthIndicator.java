package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.autoconfigure.PrintProperties;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 检查 PDF 字体供应器和临时目录的真实可用性。
 *
 * @author leyland
 */
public final class PrintInfrastructureHealthIndicator implements HealthIndicator {

    /** 宿主提供的 PDF 字体。 */
    private final List<PdfFont> fonts;

    /** 用于解析可信临时目录的打印配置。 */
    private final PrintProperties properties;

    /**
     * @param fonts 已按 Spring 顺序收集的字体
     * @param properties 打印配置
     */
    public PrintInfrastructureHealthIndicator(List<PdfFont> fonts, PrintProperties properties) {
        this.fonts = List.copyOf(Objects.requireNonNull(fonts, "fonts 不能为空"));
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /** 健康详情只说明失败组件，不包含路径、字体或异常原文。 */
    @Override
    public Health health() {
        if (!fontsAvailable()) {
            return Health.down().withDetail("component", "fonts").build();
        }
        if (!temporaryDirectoryAvailable()) {
            return Health.down().withDetail("component", "temporary-directory").build();
        }
        return Health.up()
                .withDetail("fonts", fonts.size())
                .withDetail("temporaryDirectory", "available")
                .build();
    }

    /** 每个字体流只读取少量头部并立即关闭。 */
    private boolean fontsAvailable() {
        for (PdfFont font : fonts) {
            try (InputStream stream = font.openStream()) {
                if (stream.readNBytes(32).length == 0) {
                    return false;
                }
            } catch (IOException | RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

    /** 使用探针文件确认配置目录或 PDF 默认目录具备真实写入能力。 */
    private boolean temporaryDirectoryAvailable() {
        Path root;
        try {
            root = properties.temporaryRoot().orElseGet(
                    () -> Path.of(System.getProperty("java.io.tmpdir"), "letool", "print-pdf"));
        } catch (RuntimeException exception) {
            return false;
        }

        Path probe;
        try {
            Files.createDirectories(root);
            probe = Files.createTempFile(root, ".letool-print-health-", ".tmp");
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        try {
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
