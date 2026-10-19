package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.xml.internal.engine.CompiledTemplatePlan;

/**
 * 将安全 XML 编译快照与只读上下文绑定为通用文档模型。
 *
 * <p>支持基础标签、受限动态结构、片段引用、格式计划和可信扩展计划。</p>
 *
 * @author leyland
 */
public final class XmlTemplateBinder {

    /**
     * 绑定编译模板和只读上下文。
     *
     * @param template 完成安全编译的 XML 模板
     * @param context 版本匹配的只读上下文
     * @return 完成结构校验的通用文档模型
     */
    public DocumentModel bind(CompiledXmlTemplate template, PrintContext context) {
        return CompiledTemplatePlan.bind(template == null ? null : template.plan(), context);
    }
}
