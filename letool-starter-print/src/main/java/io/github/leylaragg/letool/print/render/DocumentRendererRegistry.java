package io.github.leylaragg.letool.print.render;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按输出格式保存文档渲染器的不可变注册表。
 *
 * <p>注册顺序只用于稳定展示；同一种输出格式始终只能由一个渲染器负责。</p>
 *
 * @author leyland
 */
public final class DocumentRendererRegistry {

    /** 构造阶段冻结的渲染器索引。 */
    private final Map<OutputFormat, DocumentRenderer> renderers;

    /**
     * 创建可供打印管线并发读取的渲染器快照。
     *
     * @param renderers 至少包含一个有效渲染器的集合
     * @throws PrintPipelineException 集合为空、成员无效或输出格式重复时抛出
     * @throws NullPointerException 渲染器集合为空时抛出
     */
    public DocumentRendererRegistry(Collection<? extends DocumentRenderer> renderers) {
        Objects.requireNonNull(renderers, "renderers 不能为空");
        if (renderers.isEmpty()) {
            throw PrintPipelineException.invalidRegistration("至少需要一个文档渲染器");
        }

        Map<OutputFormat, DocumentRenderer> snapshot = new LinkedHashMap<>();
        for (DocumentRenderer renderer : renderers) {
            if (renderer == null || renderer.outputFormat() == null) {
                throw PrintPipelineException.invalidRegistration("文档渲染器及其输出格式不能为空");
            }
            OutputFormat outputFormat = renderer.outputFormat();
            if (snapshot.putIfAbsent(outputFormat, renderer) != null) {
                throw PrintPipelineException.invalidRegistration(
                        "文档渲染器输出格式重复：" + outputFormat.value());
            }
        }
        this.renderers = Collections.unmodifiableMap(snapshot);
    }

    /**
     * 取得目标格式唯一对应的渲染器。
     *
     * @param outputFormat 目标输出格式
     * @return 已注册的文档渲染器
     * @throws PrintPipelineException 没有渲染器支持目标格式时抛出
     * @throws NullPointerException 输出格式为空时抛出
     */
    public DocumentRenderer require(OutputFormat outputFormat) {
        Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        DocumentRenderer renderer = renderers.get(outputFormat);
        if (renderer == null) {
            throw PrintPipelineException.outputNotSupported(outputFormat);
        }
        return renderer;
    }

    /**
     * 返回与注册顺序一致的只读输出格式。
     *
     * @return 已注册输出格式
     */
    public Set<OutputFormat> registeredFormats() {
        return renderers.keySet();
    }
}
