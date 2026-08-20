package io.github.leylaragg.letool.print.pipeline;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintOutput;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.render.BoundedRenderOutput;

import java.io.OutputStream;
import java.util.Objects;

/**
 * 基于模板格式注册表执行路由和统一产物校验的默认打印引擎。
 *
 * <p>该实现只持有不可变注册表，可被多个线程并发调用。</p>
 *
 * @author leyland
 */
public final class DefaultPrintEngine implements PrintEngine {

    /** 只读打印管线注册表。 */
    private final PrintPipelineRegistry registry;

    /**
     * 创建默认打印引擎。
     *
     * @param registry 只读打印管线注册表
     * @throws NullPointerException 注册表为空时抛出
     */
    public DefaultPrintEngine(PrintPipelineRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
    }

    /**
     * 路由请求、执行管线并校验最终产物。
     *
     * @param request 同步打印请求
     * @return 通过统一格式和大小校验的产物
     * @throws BaseException 打印框架已分类的校验、路由或执行异常
     */
    @Override
    public PrintArtifact render(PrintRequest request) {
        validateRequest(request);
        BoundedRenderOutput memory = new BoundedRenderOutput(request.options().maxOutputBytes());
        PrintResult result = renderTo(request, memory);
        try {
            return PrintArtifact.from(result, memory.toByteArray());
        } catch (BoundedRenderOutput.OutputLimitExceededException exception) {
            throw PrintPipelineException.outputLimitExceeded(request.options().maxOutputBytes());
        }
    }

    /**
     * 路由请求并把产物直接写入调用方目标。
     *
     * @param request 同步打印请求
     * @param target 调用方拥有的输出流
     * @return 不保留正文的流式结果
     * @throws BaseException 打印框架已分类的校验、路由、渲染或写出异常
     */
    @Override
    public PrintResult renderTo(PrintRequest request, OutputStream target) {
        validateRequest(request);
        if (target == null) {
            throw PrintValidationException.invalidRequest("输出流不能为空");
        }
        PrintPipeline pipeline = registry.require(request.template().templateFormat());
        if (!pipeline.supportedOutputs().contains(request.outputFormat())) {
            throw PrintPipelineException.outputNotSupported(request.outputFormat());
        }
        PrintOutput output = new PrintOutput(target, request.options().maxOutputBytes());
        try {
            PrintResult result = pipeline.render(request, output);
            validateResult(request, output, result);
            return result;
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PrintPipelineException.executionFailed(
                    request.template().templateFormat(), exception);
        }
    }

    /** 保证请求问题在创建任何输出状态前被拒绝。 */
    private static void validateRequest(PrintRequest request) {
        if (request == null) {
            throw PrintValidationException.invalidRequest("请求不能为空");
        }
    }

    /** 只接受当前受控输出完成且格式匹配的结果。 */
    private static void validateResult(
            PrintRequest request,
            PrintOutput output,
            PrintResult result) {
        if (!output.completedWith(result)
                || !request.outputFormat().equals(result.outputFormat())) {
            throw PrintPipelineException.executionFailed(
                    request.template().templateFormat(),
                    new IllegalStateException("管线没有正确完成当前输出"));
        }
    }
}
