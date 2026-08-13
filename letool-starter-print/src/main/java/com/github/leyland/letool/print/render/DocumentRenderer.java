package com.github.leyland.letool.print.render;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentModel;

/**
 * 把通用文档模型导出为一种共享文档语义格式的渲染器。
 *
 * <p>实现必须线程安全或隔离每次调用状态。具有独立报表模型的格式应实现顶层打印管线，
 * 而不是通过此接口扩张通用文档模型。</p>
 *
 * @author leyland
 */
public interface DocumentRenderer {

    /** @return 渲染器唯一输出格式 */
    OutputFormat outputFormat();

    /** @return 渲染器支持的文档节点能力 */
    OutputCapability capability();

    /**
     * 渲染经过能力检查的文档。
     *
     * @param document 不可变通用文档模型
     * @param options 通用渲染限制
     * @return 不可变渲染输出
     */
    RenderedDocument render(DocumentModel document, RenderOptions options);
}
