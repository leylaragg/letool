package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;

/**
 * 完成 XML 安全检查和结构校验后的不透明模板快照。
 *
 * <p>公开 API 只暴露稳定版本信息，内部编译树不包含 DOM 或 StAX 对象。</p>
 *
 * @author leyland
 */
public final class CompiledXmlTemplate {

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
    CompiledXmlTemplate(String templateCode, int dslVersion, long templateSetVersion,
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

    /** @return 包内不可变编译根节点 */
    CompiledXmlNode root() {
        return root;
    }

    /** @return 包内不可变文档绑定计划 */
    CompiledDocumentPlan documentPlan() {
        return documentPlan;
    }
}
