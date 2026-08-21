package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.render.DocumentRenderer;

/**
 * 将通用文档模型输出为 PDF 的渲染器扩展契约。
 *
 * <p>宿主实现该接口即可替换默认 PDF 链路，不需要继承框架实现类。</p>
 *
 * @author leyland
 */
public interface PdfRenderer extends DocumentRenderer {
}
