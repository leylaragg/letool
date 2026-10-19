package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.xml.internal.engine.CompiledTemplatePlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 在发布阶段解析 XML 模板集合和片段引用图的稳定入口。
 *
 * @author leyland
 */
public final class XmlTemplateSetCompiler {

    /** 宿主已经配置好格式化器、表达式和标签扩展的单模板编译器。 */
    private final XmlTemplateCompiler templateCompiler;

    /** 使用 XML 模块的默认能力编译模板集合。 */
    public XmlTemplateSetCompiler() {
        this(new XmlTemplateCompiler());
    }

    /**
     * 使用宿主配置好的单模板编译器，让集合编译沿用已有扩展能力。
     *
     * @param templateCompiler XML 单模板编译器
     */
    public XmlTemplateSetCompiler(XmlTemplateCompiler templateCompiler) {
        this.templateCompiler = Objects.requireNonNull(templateCompiler, "templateCompiler 不能为空");
    }

    /**
     * 编译一个不可变模板集合。
     *
     * @param templateSet 已完成基础治理的模板集合
     * @return 可并发复用的 XML 编译快照
     */
    public CompiledXmlTemplateSet compile(TemplateSet templateSet) {
        Map<String, CompiledTemplatePlan> plans = templateCompiler.engine().compileSet(templateSet);
        Map<String, CompiledXmlTemplate> documents = new LinkedHashMap<>();
        for (Map.Entry<String, CompiledTemplatePlan> entry : plans.entrySet()) {
            documents.put(entry.getKey(), new CompiledXmlTemplate(entry.getValue()));
        }
        return new CompiledXmlTemplateSet(templateSet.version(), templateSet.digest(), documents);
    }
}
