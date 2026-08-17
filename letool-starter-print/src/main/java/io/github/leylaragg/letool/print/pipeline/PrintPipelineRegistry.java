package io.github.leylaragg.letool.print.pipeline;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按模板格式索引的不可变打印管线注册表。
 *
 * <p>构造完成后只读，可安全地被多个请求并发查询。</p>
 *
 * @author leyland
 */
public final class PrintPipelineRegistry {

    /** 模板格式到唯一完整管线的只读快照。 */
    private final Map<TemplateFormat, PrintPipeline> pipelines;

    /**
     * 创建管线注册表。
     *
     * @param pipelines 待注册管线列表，允许为空但不允许空元素
     * @throws PrintPipelineException 格式重复或管线声明不完整时抛出
     */
    public PrintPipelineRegistry(List<PrintPipeline> pipelines) {
        if (pipelines == null) {
            throw PrintPipelineException.invalidRegistration("管线列表不能为空");
        }
        Map<TemplateFormat, PrintPipeline> registered = new LinkedHashMap<>();
        for (PrintPipeline pipeline : pipelines) {
            if (pipeline == null || pipeline.templateFormat() == null) {
                throw PrintPipelineException.invalidRegistration("管线和模板格式不能为空");
            }
            Set<OutputFormat> outputs = pipeline.supportedOutputs();
            if (outputs == null || outputs.isEmpty()
                    || outputs.stream().anyMatch(java.util.Objects::isNull)) {
                throw PrintPipelineException.invalidRegistration("管线必须声明非空输出格式集合");
            }
            PrintPipeline previous = registered.putIfAbsent(pipeline.templateFormat(), pipeline);
            if (previous != null) {
                throw PrintPipelineException.duplicate(pipeline.templateFormat());
            }
        }
        this.pipelines = Map.copyOf(registered);
    }

    /**
     * 取得处理指定模板格式的唯一管线。
     *
     * @param format 模板格式
     * @return 已注册管线
     * @throws PrintPipelineException 没有对应管线时抛出
     */
    public PrintPipeline require(TemplateFormat format) {
        if (format == null) {
            throw PrintPipelineException.invalidRegistration("待查找模板格式不能为空");
        }
        PrintPipeline pipeline = pipelines.get(format);
        if (pipeline == null) {
            throw PrintPipelineException.notFound(format);
        }
        return pipeline;
    }

    /** @return 不可修改的已注册模板格式集合 */
    public Set<TemplateFormat> registeredFormats() {
        return pipelines.keySet();
    }
}
