package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;
import io.github.leylaragg.letool.print.xml.internal.engine.CompiledTemplatePlan;

/**
 * 完成 XML 安全检查和结构校验后的不透明模板快照。
 *
 * <p>公开 API 只暴露稳定版本信息，内部编译树不包含 DOM 或 StAX 对象。</p>
 *
 * @author leyland
 */
public final class CompiledXmlTemplate {

    /** 公开快照持有内部计划，宿主无需接触编译树或绑定实现。 */
    private final CompiledTemplatePlan plan;

    /** 包装已经完成编译和静态检查的内部计划。 */
    CompiledXmlTemplate(CompiledTemplatePlan plan) {
        this.plan = plan;
    }

    /** @return 稳定模板代码 */
    public String templateCode() {
        return plan.templateCode();
    }

    /** @return DSL 主版本 */
    public int dslVersion() {
        return plan.dslVersion();
    }

    /** @return 模板集合版本 */
    public long templateSetVersion() {
        return plan.templateSetVersion();
    }

    /** @return 上下文契约版本 */
    public int contextVersion() {
        return plan.contextVersion();
    }

    /**
     * 返回模板编译时固化的静态契约。
     *
     * @return 不可变模板检查结果
     */
    public TemplateInspection inspection() {
        return plan.inspection();
    }

    /** 仅供同包绑定入口传递不透明计划。 */
    CompiledTemplatePlan plan() {
        return plan;
    }
}
