package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.print.exception.PrintErrorCode;

import java.io.Serial;

/**
 * 模板源违反格式、安全或 DSL 结构约束时抛出的异常。
 *
 * <p>异常详情只包含模板代码、标签和安全位置，不回显模板正文或外部资源内容。</p>
 *
 * @author leyland
 */
public final class PrintCompilationException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建模板编译异常。 */
    private PrintCompilationException(String detail, Throwable cause) {
        super(PrintErrorCode.TEMPLATE_COMPILATION_FAILED, new Object[]{requireDetail(detail)}, null, cause);
    }

    /**
     * 创建没有底层原因的模板编译异常。
     *
     * @param detail 可安全展示的编译详情
     * @return 模板编译异常
     */
    public static PrintCompilationException invalid(String detail) {
        return new PrintCompilationException(detail, null);
    }

    /**
     * 创建保留技术原因链的模板编译异常。
     *
     * @param detail 可安全展示的编译详情
     * @param cause 底层解析异常
     * @return 模板编译异常
     */
    public static PrintCompilationException invalid(String detail, Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new PrintCompilationException(detail, cause);
    }

    /** 校验可安全展示的错误详情。 */
    private static String requireDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail 不能为空");
        }
        return detail;
    }
}
