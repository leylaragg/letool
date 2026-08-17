package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.pdf.PdfDocumentRenderer;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pipeline.DefaultPrintEngine;
import io.github.leylaragg.letool.print.pipeline.PrintPipeline;
import io.github.leylaragg.letool.print.pipeline.PrintPipelineRegistry;
import io.github.leylaragg.letool.print.render.DocumentRenderer;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import io.github.leylaragg.letool.print.service.PrintDefinitionRegistry;
import io.github.leylaragg.letool.print.service.PrintRuntimeSettings;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateSetValidator;
import io.github.leylaragg.letool.print.xml.XmlPrintPipeline;
import io.github.leylaragg.letool.print.xml.XmlTemplateBinder;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationCache;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompilationService;
import io.github.leylaragg.letool.print.xml.XmlTemplateCompiler;
import io.github.leylaragg.letool.print.xml.XmlTemplateSetCompiler;
import io.github.leylaragg.letool.print.xml.XmlTemplateSetValidator;
import io.github.leylaragg.letool.print.xml.expression.PrintConditionExpression;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.format.PrintFormatterRegistry;
import io.github.leylaragg.letool.print.xml.format.PrintValueFormatter;
import io.github.leylaragg.letool.print.xml.tag.PrintTagHandler;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 组装动态打印默认仓库、XML 编译、PDF 渲染和业务门面。
 *
 * <p>所有业务扩展都先按 Spring 顺序收集，再交给不可变注册表统一检查重复语义。</p>
 *
 * @author leyland
 */
@AutoConfiguration(after = PrintSpelAutoConfiguration.class)
@ConditionalOnClass({PrintEngine.class, XmlPrintPipeline.class, PdfDocumentRenderer.class})
@ConditionalOnProperty(
        prefix = "letool.print",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(PrintProperties.class)
public class PrintAutoConfiguration {

    /**
     * @param properties 已完成 Spring 绑定的打印配置
     * @return 业务门面和 XML 管线共享的不可变运行时配置
     */
    @Bean
    @ConditionalOnMissingBean
    public PrintRuntimeSettings printRuntimeSettings(PrintProperties properties) {
        return properties.toRuntimeSettings();
    }

    /** @return 未声明持久化仓库时使用的线程安全内存仓库 */
    @Bean
    @ConditionalOnMissingBean(TemplateRepository.class)
    public TemplateRepository templateRepository() {
        return new InMemoryTemplateRepository();
    }

    /**
     * 合并框架内置格式化器和宿主扩展。
     *
     * @param formatters 宿主声明的格式化器
     * @return 不可变格式化器注册表
     */
    @Bean
    @ConditionalOnMissingBean(PrintFormatterRegistry.class)
    public PrintFormatterRegistry printFormatterRegistry(
            ObjectProvider<PrintValueFormatter> formatters) {
        List<PrintValueFormatter> merged = new ArrayList<>(BuiltInPrintFormatters.formatters());
        formatters.orderedStream().forEach(merged::add);
        return new PrintFormatterRegistry(merged);
    }

    /**
     * @param expressions 宿主显式启用的表达式提供方
     * @return 不可变表达式注册表
     */
    @Bean
    @ConditionalOnMissingBean(PrintExpressionRegistry.class)
    public PrintExpressionRegistry printExpressionRegistry(
            ObjectProvider<PrintConditionExpression> expressions) {
        return new PrintExpressionRegistry(expressions.orderedStream().toList());
    }

    /**
     * @param handlers 可信 Java 自定义标签处理器
     * @return 不可变标签注册表
     */
    @Bean
    @ConditionalOnMissingBean(PrintTagRegistry.class)
    public PrintTagRegistry printTagRegistry(
            ObjectProvider<PrintTagHandler> handlers) {
        return new PrintTagRegistry(handlers.orderedStream().toList());
    }

    /**
     * @param formatterRegistry 已冻结的字段格式化器
     * @param expressionRegistry 已冻结的条件表达式
     * @param tagRegistry 已冻结的 Java 标签扩展
     * @return 使用全部扩展的 XML 单模板编译器
     */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateCompiler.class)
    public XmlTemplateCompiler xmlTemplateCompiler(
            PrintFormatterRegistry formatterRegistry,
            PrintExpressionRegistry expressionRegistry,
            PrintTagRegistry tagRegistry) {
        return new XmlTemplateCompiler(
                formatterRegistry, expressionRegistry, tagRegistry);
    }

    /**
     * @param templateCompiler 单模板编译器
     * @return 复用单模板能力的 XML 集合编译器
     */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateSetCompiler.class)
    public XmlTemplateSetCompiler xmlTemplateSetCompiler(
            XmlTemplateCompiler templateCompiler) {
        return new XmlTemplateSetCompiler(templateCompiler);
    }

    /**
     * @param compiler XML 集合编译器
     * @param properties 缓存容量配置
     * @return 使用外部化容量创建的双层本地编译缓存
     */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateCompilationCache.class)
    public XmlTemplateCompilationCache xmlTemplateCompilationCache(
            XmlTemplateSetCompiler compiler, PrintProperties properties) {
        properties.validateInfrastructureSettings();
        return new XmlTemplateCompilationCache(
                compiler,
                properties.getTemplateSetCacheCapacity(),
                properties.getTemplateCacheCapacity());
    }

    /**
     * @param cache 发布与运行时共用的编译缓存
     * @return XML 发布校验器
     */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateSetValidator.class)
    public XmlTemplateSetValidator xmlTemplateSetValidator(
            XmlTemplateCompilationCache cache) {
        return XmlTemplateSetValidator.using(cache);
    }

    /**
     * @param repository 已确定的模板仓库
     * @param validators 框架和宿主声明的发布校验器
     * @return 可直接用于发布模板集合的编排器
     */
    @Bean
    @ConditionalOnMissingBean(TemplateSetPublisher.class)
    public TemplateSetPublisher templateSetPublisher(
            TemplateRepository repository,
            ObjectProvider<TemplateSetValidator> validators) {
        return new TemplateSetPublisher(
                repository, validators.orderedStream().toList());
    }

    /**
     * @param repository 模板仓库
     * @param cache XML 编译缓存
     * @return 按仓库版本解析 XML 编译快照的服务
     */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateCompilationService.class)
    public XmlTemplateCompilationService xmlTemplateCompilationService(
            TemplateRepository repository, XmlTemplateCompilationCache cache) {
        return new XmlTemplateCompilationService(repository, cache);
    }

    /** @return 无共享请求状态的 XML 数据绑定器 */
    @Bean
    @ConditionalOnMissingBean(XmlTemplateBinder.class)
    public XmlTemplateBinder xmlTemplateBinder() {
        return new XmlTemplateBinder();
    }

    /**
     * @param fonts 宿主拥有并授权使用的字体
     * @param properties PDF 临时目录配置
     * @return 默认 PDF 文档渲染器
     */
    @Bean
    @ConditionalOnMissingBean(PdfDocumentRenderer.class)
    public PdfDocumentRenderer pdfDocumentRenderer(
            ObjectProvider<PdfFont> fonts, PrintProperties properties) {
        List<PdfFont> configuredFonts = fonts.orderedStream().toList();
        Optional<Path> temporaryRoot = properties.temporaryRoot();
        return temporaryRoot
                .map(path -> new PdfDocumentRenderer(configuredFonts, path))
                .orElseGet(() -> new PdfDocumentRenderer(configuredFonts));
    }

    /**
     * @param renderers 默认 PDF 与宿主自定义文档渲染器
     * @return 按输出格式冻结的渲染器注册表
     */
    @Bean
    @ConditionalOnMissingBean(DocumentRendererRegistry.class)
    public DocumentRendererRegistry documentRendererRegistry(
            ObjectProvider<DocumentRenderer> renderers) {
        return new DocumentRendererRegistry(renderers.orderedStream().toList());
    }

    /**
     * @param compilationService XML 编译快照服务
     * @param binder XML 数据绑定器
     * @param rendererRegistry 文档渲染器注册表
     * @param settings 不可变运行时配置
     * @return 默认 Letool XML 完整打印管线
     */
    @Bean
    @ConditionalOnMissingBean(XmlPrintPipeline.class)
    public XmlPrintPipeline xmlPrintPipeline(
            XmlTemplateCompilationService compilationService,
            XmlTemplateBinder binder,
            DocumentRendererRegistry rendererRegistry,
            PrintRuntimeSettings settings) {
        return new XmlPrintPipeline(
                compilationService,
                binder,
                rendererRegistry,
                settings.rendererProfileVersion());
    }

    /**
     * @param pipelines 默认 XML 与宿主自定义顶层打印管线
     * @return 按模板格式冻结的打印管线注册表
     */
    @Bean
    @ConditionalOnMissingBean(PrintPipelineRegistry.class)
    public PrintPipelineRegistry printPipelineRegistry(
            ObjectProvider<PrintPipeline> pipelines) {
        return new PrintPipelineRegistry(pipelines.orderedStream().toList());
    }

    /**
     * @param registry 顶层打印管线注册表
     * @return 执行统一路由和产物校验的默认打印引擎
     */
    @Bean
    @ConditionalOnMissingBean(PrintEngine.class)
    public PrintEngine printEngine(PrintPipelineRegistry registry) {
        return new DefaultPrintEngine(registry);
    }

    /**
     * @param definitions 宿主声明的类型化业务打印定义
     * @return 不可变业务定义注册表
     */
    @Bean
    @ConditionalOnMissingBean(PrintDefinitionRegistry.class)
    public PrintDefinitionRegistry printDefinitionRegistry(
            ObjectProvider<PrintDefinition<?>> definitions) {
        return new PrintDefinitionRegistry(definitions.orderedStream().toList());
    }

    /**
     * @param repository 模板仓库
     * @param definitionRegistry 业务打印定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     * @return 使用当前或历史模板版本生成 PDF 的业务门面
     */
    @Bean
    @ConditionalOnMissingBean(PrintService.class)
    public PrintService printService(
            TemplateRepository repository,
            PrintDefinitionRegistry definitionRegistry,
            PrintEngine engine,
            PrintRuntimeSettings settings) {
        return new PrintService(repository, definitionRegistry, engine, settings);
    }
}
