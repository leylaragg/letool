package io.github.leylaragg.letool.print.pipeline;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.exception.PrintPipelineException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

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
        if (request == null) {
            throw PrintValidationException.invalidRequest("请求不能为空");
        }
        PrintPipeline pipeline = registry.require(request.template().templateFormat());
        if (!pipeline.supportedOutputs().contains(request.outputFormat())) {
            throw PrintPipelineException.outputNotSupported(request.outputFormat());
        }
        try {
            PrintArtifact artifact = pipeline.render(request);
            validateArtifact(request, artifact);
            return artifact;
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PrintPipelineException.executionFailed(
                    request.template().templateFormat(), exception);
        }
    }

    /** 统一校验管线返回的格式和输出大小。 */
    private static void validateArtifact(PrintRequest request, PrintArtifact artifact) {
        if (artifact == null || !request.outputFormat().equals(artifact.outputFormat())) {
            throw PrintPipelineException.executionFailed(
                    request.template().templateFormat(),
                    new IllegalStateException("管线返回空产物或错误输出格式"));
        }
        if (artifact.contentLength() > request.options().maxOutputBytes()) {
            throw PrintPipelineException.outputLimitExceeded(request.options().maxOutputBytes());
        }
    }
}
