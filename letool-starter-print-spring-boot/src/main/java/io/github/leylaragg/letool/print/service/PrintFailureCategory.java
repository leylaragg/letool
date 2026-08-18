package io.github.leylaragg.letool.print.service;

import io.github.leylaragg.letool.print.exception.PrintAdapterException;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.PrintCompilationException;

import java.io.IOException;

/**
 * 打印观测使用的固定失败分类。
 *
 * <p>枚举值不会包含异常类名或消息，可安全用作低基数指标标签。</p>
 *
 * @author leyland
 */
public enum PrintFailureCategory {

    /** 成功执行，没有失败分类。 */
    NONE("none"),

    /** 请求、模板选择或上下文校验失败。 */
    VALIDATION("validation"),

    /** 业务数据适配失败。 */
    ADAPTER("adapter"),

    /** 模板编译失败。 */
    COMPILATION("compilation"),

    /** 文档渲染失败。 */
    RENDERING("rendering"),

    /** 字体、文件或其他 IO 资源失败。 */
    RESOURCE("resource"),

    /** 打印管线路由或执行失败。 */
    PIPELINE("pipeline"),

    /** 尚未归入框架稳定分类的运行时故障。 */
    UNEXPECTED("unexpected");

    /** 指标标签使用的小写稳定值。 */
    private final String value;

    /**
     * 保存稳定标签值。
     *
     * @param value 小写低基数标签
     */
    PrintFailureCategory(String value) {
        this.value = value;
    }

    /** @return 低基数小写标签值 */
    public String value() {
        return value;
    }

    /**
     * 按公开异常边界归类，不读取异常消息。
     *
     * @param failure 打印主链路抛出的运行时异常
     * @return 对应的稳定失败分类
     */
    static PrintFailureCategory from(RuntimeException failure) {
        if (failure instanceof PrintValidationException) {
            return VALIDATION;
        }
        if (failure instanceof PrintAdapterException) {
            return ADAPTER;
        }
        if (failure instanceof PrintCompilationException) {
            return COMPILATION;
        }
        if (failure instanceof PrintRenderingException) {
            return hasIoCause(failure) ? RESOURCE : RENDERING;
        }
        if (failure instanceof PrintPipelineException) {
            return PIPELINE;
        }
        return hasIoCause(failure) ? RESOURCE : UNEXPECTED;
    }

    /**
     * 沿原因链查找 IO 类型，不读取其中可能包含路径的消息。
     *
     * @param failure 待检查的异常
     * @return 原因链是否包含 IO 故障
     */
    private static boolean hasIoCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
