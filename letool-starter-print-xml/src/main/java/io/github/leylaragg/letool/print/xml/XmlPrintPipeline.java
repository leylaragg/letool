package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.node.DocumentNode;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.pipeline.PrintPipeline;
import io.github.leylaragg.letool.print.render.DocumentRenderer;
import io.github.leylaragg.letool.print.render.DocumentRendererRegistry;
import io.github.leylaragg.letool.print.render.OutputCapability;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;

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
     * @param output 由打印引擎创建的受控输出
     * @return 由当前输出完成的结果
     * @throws BaseException 已分类的模板、绑定、能力或渲染异常
     */
    @Override
    public PrintResult render(PrintRequest request, PrintOutput output) {
        if (request == null) {
            throw PrintValidationException.invalidRequest("请求不能为空");
        }
        if (output == null) {
            throw PrintValidationException.invalidRequest("打印输出不能为空");
        }
        if (!TemplateFormat.LETOOL_XML.equals(request.template().templateFormat())) {
            throw PrintValidationException.invalidRequest("XML 管线收到不匹配的模板格式");
        }

        try {
            ResolvedXmlTemplate resolved = compilationService.resolve(
                    request.template(), rendererProfileVersion, request.outputFormat());
            DocumentRenderer renderer = rendererRegistry.require(request.outputFormat());
            OutputCapability capability = Objects.requireNonNull(
                    renderer.capability(), "文档渲染器能力不能为空");
            requireStaticCapability(resolved.inspection(), capability);
            DocumentModel document = binder.bind(resolved.template(), request.context());
            // 绑定后再核对一次，可信扩展的实际结果也不能越过输出能力。
            capability.requireSupports(document);
            PrintResult result = renderDocument(renderer, document, request, output);
            if (!output.completedWith(result)
                    || !request.outputFormat().equals(result.outputFormat())) {
                throw new IllegalStateException("文档渲染器没有正确完成当前输出");
            }
            return result;
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 未知扩展消息只留在原因链，公开异常使用稳定的管线错误。
            throw PrintPipelineException.executionFailed(TemplateFormat.LETOOL_XML, exception);
        }
    }

    /** 静态检查覆盖模板所有分支，失败时不会读取请求中的业务上下文。 */
    private void requireStaticCapability(
            TemplateInspection inspection, OutputCapability capability) {
        for (Class<? extends DocumentNode> nodeType : inspection.nodeTypes()) {
            if (!capability.supports(nodeType)) {
                throw PrintValidationException.invalidDocument(
                        "输出实现不支持节点类型：" + nodeType.getSimpleName());
            }
        }
        for (var feature : inspection.features()) {
            if (!capability.supports(feature)) {
                throw PrintValidationException.invalidDocument(
                        "输出实现不支持文档特性：" + feature);
            }
        }
    }

    /** 第三方渲染器的未知异常按输出格式分类，具体消息只保留在原因链。 */
    private PrintResult renderDocument(
            DocumentRenderer renderer,
            DocumentModel document,
            PrintRequest request,
            PrintOutput output) {
        try {
            return renderer.render(document, request.options(), output);
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PrintRenderingException.renderFailed(request.outputFormat(), exception);
        }
    }
}
