package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.template.TemplateCompilationKey;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspection;

import java.util.Objects;

/**
 * 已按完整编译条件解析的不可变 XML 模板快照，可安全并发复用。
 *
 * @author leyland
 */
public final class ResolvedXmlTemplate {

    /** 本次解析锁定的全部编译条件。 */
    private final TemplateCompilationKey key;

    /** 可由后续绑定和渲染流程复用的编译结果。 */
    private final CompiledXmlTemplate template;

    /**
     * 组合编译键与对应的 XML 编译结果。
     *
     * @param key 完整编译条件
     * @param template XML 编译结果
     * @throws NullPointerException 任一快照组成部分为空时抛出
     */
    public ResolvedXmlTemplate(
            TemplateCompilationKey key, CompiledXmlTemplate template) {
        this.key = Objects.requireNonNull(key, "key 不能为空");
        this.template = Objects.requireNonNull(template, "template 不能为空");
    }

    /** @return 本次解析锁定的完整编译条件 */
    public TemplateCompilationKey key() {
        return key;
    }

    /** @return 可复用的 XML 编译结果 */
    public CompiledXmlTemplate template() {
        return template;
    }

    /** @return 当前编译快照的静态模板契约 */
    public TemplateInspection inspection() {
        return template.inspection();
    }

    @Override
    public String toString() {
        return "ResolvedXmlTemplate[key=" + key + "]";
    }
}
