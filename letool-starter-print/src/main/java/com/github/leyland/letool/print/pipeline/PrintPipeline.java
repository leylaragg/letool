package com.github.leyland.letool.print.pipeline;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.PrintArtifact;
import com.github.leyland.letool.print.api.PrintRequest;
import com.github.leyland.letool.print.api.TemplateFormat;

import java.util.Set;

/**
 * 一种模板格式从源模板到最终产物的完整打印管线。
 *
 * <p>实现必须线程安全或隔离每次请求状态，不得修改请求模板和上下文。返回产物格式必须
 * 与请求格式一致，并且不得绕过请求的输出限制。第三方故障需要保留原因链。</p>
 *
 * @author leyland
 */
public interface PrintPipeline {

    /** @return 此管线唯一处理的模板格式 */
    TemplateFormat templateFormat();

    /** @return 非空且稳定的输出格式集合 */
    Set<OutputFormat> supportedOutputs();

    /**
     * 执行完整打印生命周期。
     *
     * @param request 已校验的打印请求
     * @return 与请求输出格式一致的产物
     */
    PrintArtifact render(PrintRequest request);
}
