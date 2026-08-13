package com.github.leyland.letool.print.xml;

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

    /** 创建内部编译快照。 */
    CompiledXmlTemplate(String templateCode, int dslVersion, long templateSetVersion,
                        int contextVersion, CompiledXmlNode root) {
        this.templateCode = templateCode;
        this.dslVersion = dslVersion;
        this.templateSetVersion = templateSetVersion;
        this.contextVersion = contextVersion;
        this.root = root;
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

    /** @return 包内不可变编译根节点 */
    CompiledXmlNode root() {
        return root;
    }
}
