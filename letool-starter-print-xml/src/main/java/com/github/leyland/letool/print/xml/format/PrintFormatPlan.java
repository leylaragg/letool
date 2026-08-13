package com.github.leyland.letool.print.xml.format;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 编译完成后可直接用于单个字段值的不可变格式化计划。
 *
 * @author leyland
 */
@FunctionalInterface
public interface PrintFormatPlan {

    /**
     * 将非空 JSON 字段值转换为显示文本。
     *
     * @param value 非空 JSON 字段值
     * @return 非空显示文本
     */
    String format(JsonNode value);
}
