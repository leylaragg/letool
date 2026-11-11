package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.service.PrintRuntimeSettings;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

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

    /** 显式配置时优先于 maxOutputBytes，支持 100MB 等容量单位。 */
    private DataSize maxOutputSize;

    /** 单次 PDF 渲染允许占用的最大临时空间。 */
    private long maxTemporaryBytes = RenderOptions.DEFAULT_MAX_TEMPORARY_BYTES;

    /** 显式配置时优先于 maxTemporaryBytes，支持 300MB 等容量单位。 */
    private DataSize maxTemporarySize;

    /** 最终 PDF 文本批注数量上限，0 表示禁止文本批注。 */
    private int maxAnnotations = RenderOptions.DEFAULT_MAX_ANNOTATIONS;

    /** 单次 PDF 请求保留的字体字节预算，0 禁用二进制缓存。 */
    private long maxFontCacheBytes = RenderOptions.DEFAULT_MAX_FONT_CACHE_BYTES;

    /** 显式配置时优先于 maxFontCacheBytes，支持 64MB 等容量单位。 */
    private DataSize maxFontCacheSize;

    /** 是否把安全文档元数据写入产物。 */
    private boolean includeDocumentMetadata = true;

    /** XML 模板集合编译缓存容量。 */
    private int templateSetCacheCapacity = XmlTemplateCompilationCache.DEFAULT_TEMPLATE_SET_CAPACITY;

    /** XML 单文档解析缓存容量。 */
    private int templateCacheCapacity = XmlTemplateCompilationCache.DEFAULT_TEMPLATE_CAPACITY;

    /** PDF 渲染使用的可信临时根目录，空值沿用模块默认目录。 */
    private String temporaryDirectory = "";

    /** OpenHTMLToPDF 日志等级，未配置时保留宿主现有策略。 */
    private String pdfLogLevel;

    /** 启动阶段按需执行的严格检查。 */
    private Startup startup = new Startup();

    /** Micrometer 指标开关。 */
    private Metrics metrics = new Metrics();

    /** Actuator 健康检查开关。 */
    private Health health = new Health();

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

    /** @return 显式产物容量别名，未配置时为 null */
    public DataSize getMaxOutputSize() {
        return maxOutputSize;
    }

    /** @param maxOutputSize 优先于旧字节属性的产物容量 */
    public void setMaxOutputSize(DataSize maxOutputSize) {
        this.maxOutputSize = maxOutputSize;
    }

    /** @return 单次渲染最大临时空间字节数 */
    public long getMaxTemporaryBytes() {
        return maxTemporaryBytes;
    }

    /** @param maxTemporaryBytes 单次 PDF 请求的活动临时文件总上限 */
    public void setMaxTemporaryBytes(long maxTemporaryBytes) {
        this.maxTemporaryBytes = maxTemporaryBytes;
    }

    /** @return 显式临时空间容量别名，未配置时为 null */
    public DataSize getMaxTemporarySize() {
        return maxTemporarySize;
    }

    /** @param maxTemporarySize 优先于旧字节属性的临时空间容量 */
    public void setMaxTemporarySize(DataSize maxTemporarySize) {
        this.maxTemporarySize = maxTemporarySize;
    }

    /** @return 最终 PDF 文本批注数量上限 */
    public int getMaxAnnotations() {
        return maxAnnotations;
    }

    /** @param maxAnnotations 文本批注数量上限，0 表示禁止 */
    public void setMaxAnnotations(int maxAnnotations) {
        this.maxAnnotations = maxAnnotations;
    }

    /** @return 单次 PDF 请求保留的字体字节预算 */
    public long getMaxFontCacheBytes() {
        return maxFontCacheBytes;
    }

    /** @param maxFontCacheBytes 字体二进制缓存预算，0 禁用缓存 */
    public void setMaxFontCacheBytes(long maxFontCacheBytes) {
        this.maxFontCacheBytes = maxFontCacheBytes;
    }

    /** @return 显式字体缓存容量别名，未配置时为 null */
    public DataSize getMaxFontCacheSize() {
        return maxFontCacheSize;
    }

    /** @param maxFontCacheSize 优先于字节属性的字体二进制缓存预算 */
    public void setMaxFontCacheSize(DataSize maxFontCacheSize) {
        this.maxFontCacheSize = maxFontCacheSize;
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

    /** @return PDF 日志命名级别，未配置时为 null */
    public String getPdfLogLevel() {
        return pdfLogLevel;
    }

    /** @param pdfLogLevel PDF 日志命名级别，null 或空白表示保留宿主策略 */
    public void setPdfLogLevel(String pdfLogLevel) {
        this.pdfLogLevel = pdfLogLevel;
    }

    /** @return 可继续绑定的严格启动配置 */
    public Startup getStartup() {
        return startup;
    }

    /**
     * 替换整组严格启动配置。
     *
     * @param startup 非空启动配置
     */
    public void setStartup(Startup startup) {
        if (startup == null) {
            throw new IllegalArgumentException("letool.print.startup 配置不能为空");
        }
        this.startup = startup;
    }

    /** @return 可继续绑定的指标配置 */
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * 替换整组指标配置。
     *
     * @param metrics 非空指标配置
     */
    public void setMetrics(Metrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("letool.print.metrics 配置不能为空");
        }
        this.metrics = metrics;
    }

    /** @return 可继续绑定的健康检查配置 */
    public Health getHealth() {
        return health;
    }

    /**
     * 替换整组健康检查配置。
     *
     * @param health 非空健康检查配置
     */
    public void setHealth(Health health) {
        if (health == null) {
            throw new IllegalArgumentException("letool.print.health 配置不能为空");
        }
        this.health = health;
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
     * 将宿主配置转换为不可变运行时快照，供默认打印门面构造渲染请求。
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
            // 显式容量属性优先。未设置容量属性时沿用对应的字节配置及其默认值。
            long outputBytes = maxOutputSize == null ? maxOutputBytes : maxOutputSize.toBytes();
            long temporaryBytes = maxTemporarySize == null ? maxTemporaryBytes : maxTemporarySize.toBytes();
            long fontCacheBytes = maxFontCacheSize == null ? maxFontCacheBytes : maxFontCacheSize.toBytes();
            options = new RenderOptions(maxPages, outputBytes, temporaryBytes,
                    includeDocumentMetadata, maxAnnotations, fontCacheBytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "letool.print.max-pages、max-output-bytes/max-output-size、"
                            + "max-temporary-bytes/max-temporary-size、max-annotations 或 "
                            + "max-font-cache-bytes/max-font-cache-size 配置不合法",
                    exception);
        }
        return new PrintRuntimeSettings(
                rendererProfileVersion, resolvedLocale, resolvedZone, options);
    }

    /**
     * 校验创建缓存、PDF 渲染器和日志初始化器所需的基础配置。
     *
     * @throws IllegalArgumentException 版本、缓存容量、目录或日志等级配置不合法时抛出
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
        pdfLogLevel();
    }

    /**
     * 解析 JUL 标准命名级别和 WARN、ERROR、DEBUG、TRACE 别名。
     *
     * @return 显式配置的日志等级，未配置或只含空白时返回 {@code Optional.empty()}
     * @throws IllegalArgumentException 非空配置不是支持的命名等级时抛出
     */
    public Optional<Level> pdfLogLevel() {
        if (pdfLogLevel == null || pdfLogLevel.isBlank()) {
            return Optional.empty();
        }
        // 只接受明确的命名级别，避免数字或第三方扩展等级改变过滤含义。
        Level level = switch (pdfLogLevel.strip().toUpperCase(Locale.ROOT)) {
            case "OFF" -> Level.OFF;
            case "SEVERE", "ERROR" -> Level.SEVERE;
            case "WARNING", "WARN" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "CONFIG" -> Level.CONFIG;
            case "FINE", "DEBUG" -> Level.FINE;
            case "FINER" -> Level.FINER;
            case "FINEST", "TRACE" -> Level.FINEST;
            case "ALL" -> Level.ALL;
            default -> throw invalid("pdf-log-level");
        };
        return Optional.of(level);
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
     * 可选的启动期基础设施检查。
     *
     * @author leyland
     */
    public static class Startup {

        /** 是否要求仓库已经存在活动模板集合。 */
        private boolean requireActiveTemplate;

        /** 是否打开字体流并检查配置的临时目录。 */
        private boolean validateFonts;

        /** 严格校验时要求字体回退链覆盖的可选文本。 */
        private String fontProbeText = "";

        /** @return 是否要求活动模板集合 */
        public boolean isRequireActiveTemplate() {
            return requireActiveTemplate;
        }

        /** @param requireActiveTemplate 是否要求活动模板集合 */
        public void setRequireActiveTemplate(boolean requireActiveTemplate) {
            this.requireActiveTemplate = requireActiveTemplate;
        }

        /** @return 是否检查字体和临时目录 */
        public boolean isValidateFonts() {
            return validateFonts;
        }

        /** @param validateFonts 是否检查字体和临时目录 */
        public void setValidateFonts(boolean validateFonts) {
            this.validateFonts = validateFonts;
        }

        /** @return 字体回退链探测文本 */
        public String getFontProbeText() {
            return fontProbeText;
        }

        /** @param fontProbeText 需要由已配置字体完整覆盖的文本 */
        public void setFontProbeText(String fontProbeText) {
            this.fontProbeText = fontProbeText;
        }
    }

    /**
     * 可选 Micrometer 指标配置。
     *
     * @author leyland
     */
    public static class Metrics {

        /** 类路径具备 Micrometer 时是否注册打印指标。 */
        private boolean enabled = true;

        /** @return 是否启用打印指标 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用打印指标 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 可选 Actuator 健康检查配置。
     *
     * @author leyland
     */
    public static class Health {

        /** 类路径具备 Actuator 时是否注册打印健康检查。 */
        private boolean enabled = true;

        /** @return 是否启用打印健康检查 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用打印健康检查 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
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
