package com.github.leyland.letool.print.autoconfigure;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.service.PrintRuntimeSettings;
import com.github.leyland.letool.print.xml.XmlTemplateCompilationCache;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Optional;

/**
 * 动态打印 Starter 的 Spring Boot 外部化配置。
 *
 * <p>Spring 负责写入可变字段，创建打印组件前再统一转换为经过校验的运行时快照。</p>
 *
 * @author leyland
 */
@ConfigurationProperties(prefix = "letool.print")
public class PrintProperties {

    /** 是否启用打印 Starter 的默认装配。 */
    private boolean enabled = true;

    /** 参与 XML 编译缓存键的渲染器配置版本。 */
    private long rendererProfileVersion = 1;

    /** 业务门面构造请求时使用的语言区域。 */
    private String locale = "zh-CN";

    /** 业务门面构造请求时使用的日期时间时区。 */
    private String zoneId = "Asia/Shanghai";

    /** 单份产物允许的最大页数。 */
    private int maxPages = RenderOptions.DEFAULT_MAX_PAGES;

    /** 单份产物允许的最大字节数。 */
    private long maxOutputBytes = RenderOptions.DEFAULT_MAX_OUTPUT_BYTES;

    /** 是否把安全文档元数据写入产物。 */
    private boolean includeDocumentMetadata = true;

    /** XML 模板集合编译缓存容量。 */
    private int templateSetCacheCapacity = XmlTemplateCompilationCache.DEFAULT_TEMPLATE_SET_CAPACITY;

    /** XML 单文档解析缓存容量。 */
    private int templateCacheCapacity = XmlTemplateCompilationCache.DEFAULT_TEMPLATE_CAPACITY;

    /** PDF 渲染使用的可信临时根目录，空值沿用模块默认目录。 */
    private String temporaryDirectory = "";

    /** 可选受限 SpEL 的显式开关。 */
    private Spel spel = new Spel();

    /** @return 是否启用打印自动配置 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用打印自动配置 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 渲染器配置版本 */
    public long getRendererProfileVersion() {
        return rendererProfileVersion;
    }

    /** @param rendererProfileVersion 参与编译键的渲染器配置版本 */
    public void setRendererProfileVersion(long rendererProfileVersion) {
        this.rendererProfileVersion = rendererProfileVersion;
    }

    /** @return IETF BCP 47 区域标识 */
    public String getLocale() {
        return locale;
    }

    /** @param locale 业务请求默认区域标识 */
    public void setLocale(String locale) {
        this.locale = locale;
    }

    /** @return 日期时间时区标识 */
    public String getZoneId() {
        return zoneId;
    }

    /** @param zoneId 业务请求默认时区标识 */
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    /** @return 最大页数 */
    public int getMaxPages() {
        return maxPages;
    }

    /** @param maxPages 单份产物最大页数 */
    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /** @return 最大产物字节数 */
    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    /** @param maxOutputBytes 单份产物最大字节数 */
    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    /** @return 是否输出文档元数据 */
    public boolean isIncludeDocumentMetadata() {
        return includeDocumentMetadata;
    }

    /** @param includeDocumentMetadata 是否输出安全文档元数据 */
    public void setIncludeDocumentMetadata(boolean includeDocumentMetadata) {
        this.includeDocumentMetadata = includeDocumentMetadata;
    }

    /** @return 模板集合编译缓存容量 */
    public int getTemplateSetCacheCapacity() {
        return templateSetCacheCapacity;
    }

    /** @param templateSetCacheCapacity 模板集合编译缓存容量 */
    public void setTemplateSetCacheCapacity(int templateSetCacheCapacity) {
        this.templateSetCacheCapacity = templateSetCacheCapacity;
    }

    /** @return 单文档解析缓存容量 */
    public int getTemplateCacheCapacity() {
        return templateCacheCapacity;
    }

    /** @param templateCacheCapacity 单文档解析缓存容量 */
    public void setTemplateCacheCapacity(int templateCacheCapacity) {
        this.templateCacheCapacity = templateCacheCapacity;
    }

    /** @return PDF 临时根目录配置，空文本表示使用默认值 */
    public String getTemporaryDirectory() {
        return temporaryDirectory;
    }

    /** @param temporaryDirectory 可信 PDF 临时根目录 */
    public void setTemporaryDirectory(String temporaryDirectory) {
        this.temporaryDirectory = temporaryDirectory;
    }

    /** @return 可继续绑定的 SpEL 配置 */
    public Spel getSpel() {
        return spel;
    }

    /**
     * 替换整组 SpEL 配置。
     *
     * @param spel 非空 SpEL 配置
     */
    public void setSpel(Spel spel) {
        if (spel == null) {
            throw new IllegalArgumentException("letool.print.spel 配置不能为空");
        }
        this.spel = spel;
    }

    /**
     * 将业务请求配置转换为不可变运行时快照。
     *
     * @return 可安全复用的区域、时区和渲染限制
     * @throws IllegalArgumentException 任一配置超出边界时抛出
     */
    public PrintRuntimeSettings toRuntimeSettings() {
        validateInfrastructureSettings();
        Locale resolvedLocale = locale();
        ZoneId resolvedZone = zoneId();
        RenderOptions options;
        try {
            options = new RenderOptions(
                    maxPages, maxOutputBytes, includeDocumentMetadata);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "letool.print.max-pages 或 max-output-bytes 配置不合法", exception);
        }
        return new PrintRuntimeSettings(
                rendererProfileVersion, resolvedLocale, resolvedZone, options);
    }

    /**
     * 校验创建缓存和 PDF 渲染器所需的基础配置。
     *
     * @throws IllegalArgumentException 版本、缓存容量或目录配置不合法时抛出
     */
    public void validateInfrastructureSettings() {
        if (rendererProfileVersion <= 0) {
            throw invalid("renderer-profile-version");
        }
        if (templateSetCacheCapacity <= 0) {
            throw invalid("template-set-cache-capacity");
        }
        if (templateCacheCapacity <= 0) {
            throw invalid("template-cache-capacity");
        }
        temporaryRoot();
    }

    /**
     * 解析宿主可信的 PDF 临时根目录。
     *
     * @return 空配置或规范化的绝对路径
     * @throws IllegalArgumentException 路径语法非法或指向普通文件时抛出
     */
    public Optional<Path> temporaryRoot() {
        if (temporaryDirectory == null) {
            throw invalid("temporary-directory");
        }
        if (temporaryDirectory.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(temporaryDirectory).toAbsolutePath().normalize();
            if (Files.exists(path) && !Files.isDirectory(path)) {
                throw invalid("temporary-directory");
            }
            return Optional.of(path);
        } catch (InvalidPathException exception) {
            throw invalid("temporary-directory");
        }
    }

    /** 解析区域时不把非法配置内容带入异常消息。 */
    private Locale locale() {
        if (locale == null || locale.isBlank()) {
            throw invalid("locale");
        }
        try {
            return new Locale.Builder().setLanguageTag(locale).build();
        } catch (IllformedLocaleException exception) {
            throw invalid("locale");
        }
    }

    /** 解析时区时只暴露对应属性名。 */
    private ZoneId zoneId() {
        if (zoneId == null || zoneId.isBlank()) {
            throw invalid("zone-id");
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            throw invalid("zone-id");
        }
    }

    /**
     * @param property 配置属性的安全名称
     * @return 不回显配置值的边界异常
     */
    private IllegalArgumentException invalid(String property) {
        return new IllegalArgumentException("letool.print." + property + " 配置不合法");
    }

    /**
     * 受限 SpEL 的可绑定配置。
     *
     * @author leyland
     */
    public static class Spel {

        /** 是否显式启用受限 SpEL 提供方。 */
        private boolean enabled;

        /** @return 是否显式启用受限 SpEL */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否显式启用受限 SpEL */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
