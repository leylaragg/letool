package com.github.leyland.letool.print.xml.format;

import java.util.Map;

/**
 * 将静态格式选项编译为字段值格式化计划的扩展接口。
 *
 * <p>实现应当是无状态或线程安全的，返回的计划必须支持并发调用。</p>
 *
 * @author leyland
 */
public interface PrintValueFormatter {

    /**
     * 返回注册表中的稳定格式化器名称。
     *
     * @return 受限格式化器名称
     */
    String name();

    /**
     * 编译静态格式选项。
     *
     * @param options 不可变格式选项
     * @param context 不包含模板正文的安全编译位置
     * @return 可并发复用的格式化计划
     */
    PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context);
}
