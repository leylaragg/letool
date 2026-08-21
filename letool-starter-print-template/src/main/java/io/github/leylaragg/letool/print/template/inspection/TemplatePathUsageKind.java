package io.github.leylaragg.letool.print.template.inspection;

/**
 * 模板读取数据路径的静态用途。
 *
 * @author leyland
 */
public enum TemplatePathUsageKind {
    /** 生成字段文本。 */
    FIELD,
    /** 执行结构化条件判断。 */
    CONDITION,
    /** 提供循环集合。 */
    LOOP,
    /** 解析图片资源标识。 */
    IMAGE_RESOURCE,
    /** 向片段传入参数。 */
    INCLUDE_ARGUMENT,
    /** 受限表达式读取数据。 */
    EXPRESSION,
    /** 自定义标签读取数据。 */
    CUSTOM_TAG
}
