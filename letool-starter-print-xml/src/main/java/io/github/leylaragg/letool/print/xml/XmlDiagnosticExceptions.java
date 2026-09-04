package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

/**
 * 集中创建不回显业务内容的 XML 定位异常。
 *
 * @author leyland
 */
final class XmlDiagnosticExceptions {

    /** 工具类不允许实例化。 */
    private XmlDiagnosticExceptions() {
    }

    /**
     * 创建带源码行列的编译异常。
     *
     * @param templateCode 模板代码
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @return 模板编译异常
     */
    static PrintCompilationException source(
            String templateCode, int line, int column, String detail) {
        return PrintCompilationException.invalid(
                sourceMessage(templateCode, line, column, detail));
    }

    /**
     * 创建保留原因链的源码定位编译异常。
     *
     * @param templateCode 模板代码
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @param cause 底层技术原因
     * @return 模板编译异常
     */
    static PrintCompilationException source(
            String templateCode, int line, int column, String detail, Throwable cause) {
        return PrintCompilationException.invalid(
                sourceMessage(templateCode, line, column, detail), cause);
    }

    /**
     * 创建带标签路径和源码行列的编译异常。
     *
     * @param templateCode 模板代码
     * @param tagPath 安全标签路径
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @return 模板编译异常
     */
    static PrintCompilationException path(
            String templateCode, String tagPath, int line, int column, String detail) {
        return PrintCompilationException.invalid(
                pathMessage(templateCode, tagPath, line, column, detail));
    }

    /**
     * 创建保留原因链的标签路径定位编译异常。
     *
     * @param templateCode 模板代码
     * @param tagPath 安全标签路径
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @param cause 底层技术原因
     * @return 模板编译异常
     */
    static PrintCompilationException path(
            String templateCode, String tagPath, int line, int column,
            String detail, Throwable cause) {
        return PrintCompilationException.invalid(
                pathMessage(templateCode, tagPath, line, column, detail), cause);
    }

    /**
     * 使用已编译节点创建标签路径定位编译异常。
     *
     * @param templateCode 模板代码
     * @param node 已编译节点
     * @param detail 安全错误详情
     * @return 模板编译异常
     */
    static PrintCompilationException path(
            String templateCode, CompiledXmlNode node, String detail) {
        return path(templateCode, node.tagPath(), node.line(), node.column(), detail);
    }

    /**
     * 使用已编译节点创建保留原因链的标签路径定位编译异常。
     *
     * @param templateCode 模板代码
     * @param node 已编译节点
     * @param detail 安全错误详情
     * @param cause 底层技术原因
     * @return 模板编译异常
     */
    static PrintCompilationException path(
            String templateCode, CompiledXmlNode node, String detail, Throwable cause) {
        return path(templateCode, node.tagPath(), node.line(), node.column(), detail, cause);
    }

    /**
     * 创建带节点位置的绑定异常。
     *
     * @param templateCode 模板代码
     * @param node 已编译节点
     * @param detail 安全错误详情
     * @return 文档绑定异常
     */
    static PrintValidationException binding(
            String templateCode, CompiledXmlNode node, String detail) {
        return PrintValidationException.invalidDocument(
                pathMessage(templateCode, node.tagPath(), node.line(), node.column(), detail));
    }

    /**
     * 创建保留原因链的节点绑定异常。
     *
     * @param templateCode 模板代码
     * @param node 已编译节点
     * @param detail 安全错误详情
     * @param cause 底层技术原因
     * @return 文档绑定异常
     */
    static PrintValidationException binding(
            String templateCode, CompiledXmlNode node, String detail, Throwable cause) {
        return PrintValidationException.invalidDocument(
                pathMessage(templateCode, node.tagPath(), node.line(), node.column(), detail),
                cause);
    }

    /**
     * 生成源码行列消息。
     *
     * @param templateCode 模板代码
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @return 完整安全消息
     */
    private static String sourceMessage(
            String templateCode, int line, int column, String detail) {
        return templateCode + "：第 " + line + " 行，第 " + column + " 列：" + detail;
    }

    /**
     * 生成标签路径和源码行列消息。
     *
     * @param templateCode 模板代码
     * @param tagPath 安全标签路径
     * @param line 源码行号
     * @param column 源码列号
     * @param detail 安全错误详情
     * @return 完整安全消息
     */
    private static String pathMessage(
            String templateCode, String tagPath, int line, int column, String detail) {
        return templateCode + "：" + tagPath + "，第 " + line + " 行，第 " + column
                + " 列：" + detail;
    }
}
