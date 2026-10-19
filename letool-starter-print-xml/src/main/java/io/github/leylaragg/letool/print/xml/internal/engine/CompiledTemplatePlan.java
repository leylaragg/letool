package io.github.leylaragg.letool.print.xml.internal.engine;

import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;

/**
 * 保存 XML 执行内核的不可变编译计划，供原有公开快照委托读取和绑定。
 *
 * <p>跨包入口不暴露节点、作用域或容量治理器；这些实现仍保持包级访问。</p>
 *
 * @author leyland
 */
public final class CompiledTemplatePlan {

    /** 稳定模板代码。 */
    private final String templateCode;

    /** DSL 主版本。 */
    private final int dslVersion;

    /** 模板集合版本。 */
    private final long templateSetVersion;

    /** 上下文契约版本。 */
    private final int contextVersion;

    /** 包内使用的不可变编译根节点。 */
    private final CompiledXmlNode root;

    /** 文档绑定直接使用的静态页面与样式计划。 */
    private final CompiledDocumentPlan documentPlan;

    /** 宿主可在绑定前读取的静态检查快照。 */
    private final TemplateInspection inspection;

    /** 创建内部编译快照。 */
    CompiledTemplatePlan(String templateCode, int dslVersion, long templateSetVersion,
                        int contextVersion, CompiledXmlNode root,
                        CompiledDocumentPlan documentPlan,
                        TemplateInspection inspection) {
        this.templateCode = templateCode;
        this.dslVersion = dslVersion;
        this.templateSetVersion = templateSetVersion;
        this.contextVersion = contextVersion;
        this.root = root;
        this.documentPlan = documentPlan;
        this.inspection = inspection;
    }

    /** @return 稳定模板代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return DSL 主版本 */
    public int dslVersion() {
        return dslVersion;
    }

    /** @return 模板集合版本 */
    public long templateSetVersion() {
        return templateSetVersion;
    }

    /** @return 上下文契约版本 */
    public int contextVersion() {
        return contextVersion;
    }

    /**
     * 返回模板编译时固化的静态契约。
     *
     * @return 不可变模板检查结果
     */
    public TemplateInspection inspection() {
        return inspection;
    }

    /**
     * 将不透明计划交给绑定实现，空值及版本检查由绑定实现统一执行。
     *
     * @param template 编译完成的内部计划
     * @param context 本次绑定使用的上下文
     * @return 通过结构校验的文档模型
     */
    public static DocumentModel bind(CompiledTemplatePlan template, PrintContext context) {
        return new XmlBindingEngine().bind(template, context);
    }

    /** @return 包内不可变编译根节点 */
    CompiledXmlNode root() {
        return root;
    }

    /** @return 包内不可变文档绑定计划 */
    CompiledDocumentPlan documentPlan() {
        return documentPlan;
    }
}
