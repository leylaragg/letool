package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.PrintTemplate;

import java.util.Objects;

/**
 * 带用途的模板定义。
 *
 * @author leyland
 */
public final class TemplateDefinition {

    /** 模板在集合中的用途。 */
    private final TemplateType type;

    /** 核心模块提供的不可变模板快照。 */
    private final PrintTemplate template;

    /**
     * 创建模板定义。
     *
     * @param type 模板用途
     * @param template 模板快照
     */
    public TemplateDefinition(TemplateType type, PrintTemplate template) {
        this.type = Objects.requireNonNull(type, "type 不能为空");
        this.template = Objects.requireNonNull(template, "template 不能为空");
    }

    /** @return 模板用途 */
    public TemplateType type() {
        return type;
    }

    /** @return 模板快照 */
    public PrintTemplate template() {
        return template;
    }
}
