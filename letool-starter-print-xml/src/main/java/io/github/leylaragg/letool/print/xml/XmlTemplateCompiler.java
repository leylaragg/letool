package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionRegistry;
import io.github.leylaragg.letool.print.xml.format.BuiltInPrintFormatters;
import io.github.leylaragg.letool.print.xml.format.PrintFormatterRegistry;
import io.github.leylaragg.letool.print.xml.internal.engine.XmlCompilationEngine;
import io.github.leylaragg.letool.print.xml.tag.PrintTagRegistry;

import java.util.List;

/**
 * 将 Letool 受控 XML 模板编译为不透明快照的稳定入口。
 *
 * @author leyland
 */
public final class XmlTemplateCompiler {

    /** 编译器和集合编译器共用同一组已配置的扩展注册表。 */
    private final XmlCompilationEngine engine;

    /** 使用内置格式化器创建编译器。 */
    public XmlTemplateCompiler() {
        this(BuiltInPrintFormatters.registry(),
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
    }

    /**
     * 使用显式格式化器注册表创建编译器。
     *
     * @param formatterRegistry 编译阶段使用的不可变注册表
     */
    public XmlTemplateCompiler(PrintFormatterRegistry formatterRegistry) {
        this(formatterRegistry,
                new PrintExpressionRegistry(List.of()), new PrintTagRegistry(List.of()));
    }

    /**
     * 使用显式格式化器、表达式和自定义标签注册表创建编译器。
     *
     * @param formatterRegistry 格式化器注册表
     * @param expressionRegistry 条件表达式注册表
     * @param tagRegistry 自定义标签注册表
     */
    public XmlTemplateCompiler(
            PrintFormatterRegistry formatterRegistry,
            PrintExpressionRegistry expressionRegistry,
            PrintTagRegistry tagRegistry) {
        this.engine = new XmlCompilationEngine(formatterRegistry, expressionRegistry, tagRegistry);
    }

    /**
     * 编译模板快照。
     *
     * @param template Letool XML 模板
     * @return 完成安全和结构校验的编译快照
     * @throws NullPointerException 模板为空时抛出
     */
    public CompiledXmlTemplate compile(PrintTemplate template) {
        return new CompiledXmlTemplate(engine.compile(template));
    }

    /** 集合编译沿用当前入口已经固化的扩展配置。 */
    XmlCompilationEngine engine() {
        return engine;
    }
}
