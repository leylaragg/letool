package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pipeline.PrintPipeline;
import io.github.leylaragg.letool.print.render.DocumentRenderer;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.render.RenderedDocument;

import java.util.Objects;
import java.util.Set;

/**
 * 将 Letool XML 模板编译、绑定并交给目标文档渲染器的完整管线。
 *
 * <p>实例只持有线程安全组件和不可变配置，可以在非 Spring 环境中手动组合并并发复用。</p>
 *
 * @author leyland
 */
public final class XmlPrintPipeline implements PrintPipeline {

    /** 按仓库版本解析并复用 XML 编译快照。 */
    private final XmlTemplateCompilationService compilationService;

    /** 将编译结果和请求上下文绑定为通用文档模型。 */
    private final XmlTemplateBinder binder;

    /** 按请求输出格式选择唯一渲染器。 */
    private final DocumentRendererRegistry rendererRegistry;

    /** 参与编译缓存键的渲染能力配置版本。 */
    private final long rendererProfileVersion;

    /**
     * 创建 XML 到最终产物的同步打印管线。
     *
     * @param compilationService 模板快照解析服务
     * @param binder XML 数据绑定器
     * @param rendererRegistry 文档渲染器注册表
     * @param rendererProfileVersion 正整数渲染器配置版本
     * @throws IllegalArgumentException 渲染器配置版本不是正整数时抛出
     * @throws NullPointerException 任一协作者为空时抛出
     */
    public XmlPrintPipeline(
            XmlTemplateCompilationService compilationService,
            XmlTemplateBinder binder,
            DocumentRendererRegistry rendererRegistry,
            long rendererProfileVersion) {
        this.compilationService = Objects.requireNonNull(
                compilationService, "compilationService 不能为空");
        this.binder = Objects.requireNonNull(binder, "binder 不能为空");
        this.rendererRegistry = Objects.requireNonNull(
                rendererRegistry, "rendererRegistry 不能为空");
        if (rendererProfileVersion <= 0) {
            throw new IllegalArgumentException("rendererProfileVersion 必须为正整数");
        }
        this.rendererProfileVersion = rendererProfileVersion;
    }

    /** @return Letool 受控 XML 模板格式 */
    @Override
    public TemplateFormat templateFormat() {
        return TemplateFormat.LETOOL_XML;
    }

    /** @return 当前渲染器注册表支持的只读输出格式 */
    @Override
    public Set<OutputFormat> supportedOutputs() {
        return rendererRegistry.registeredFormats();
    }

    /**
     * 按请求锁定的仓库快照完成 XML 编译、绑定、能力检查和渲染。
     *
     * @param request 已锁定模板和只读上下文的同步请求
     * @return 与请求输出格式一致的不可变产物
     * @throws BaseException 已分类的模板、绑定、能力或渲染异常
     */
    @Override
    public PrintArtifact render(PrintRequest request) {
        if (request == null) {
            throw PrintValidationException.invalidRequest("请求不能为空");
        }
        if (!TemplateFormat.LETOOL_XML.equals(request.template().templateFormat())) {
            throw PrintValidationException.invalidRequest("XML 管线收到不匹配的模板格式");
        }

        try {
            ResolvedXmlTemplate resolved = compilationService.resolve(
                    request.template(), rendererProfileVersion, request.outputFormat());
            DocumentModel document = binder.bind(resolved.template(), request.context());
            DocumentRenderer renderer = rendererRegistry.require(request.outputFormat());
            // 第三方渲染器不能依靠自身实现决定是否忽略未知文档节点。
            renderer.capability().requireSupports(document);
            RenderedDocument rendered = renderer.render(document, request.options());
            if (rendered == null || !request.outputFormat().equals(rendered.outputFormat())) {
                throw new IllegalStateException("文档渲染器返回空结果或错误输出格式");
            }
            return PrintArtifact.of(
                    rendered.outputFormat(), rendered.content(), rendered.metadata());
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 未知扩展消息只留在原因链，公开异常使用稳定的管线错误。
            throw PrintPipelineException.executionFailed(TemplateFormat.LETOOL_XML, exception);
        }
    }
}
