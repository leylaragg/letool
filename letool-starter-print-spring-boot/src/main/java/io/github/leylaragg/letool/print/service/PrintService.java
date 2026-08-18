package io.github.leylaragg.letool.print.service;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintAdapterException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateType;

import java.util.Objects;

/**
 * 通过业务定义编码生成 PDF 的同步打印门面。
 *
 * <p>门面只负责锁定模板版本、调用数据适配器和构造通用请求，不进入业务查询或权限实现。</p>
 *
 * @author leyland
 */
public final class PrintService {

    /** 提供当前和历史模板集合快照。 */
    private final TemplateRepository repository;

    /** 保存宿主声明的业务打印定义。 */
    private final PrintDefinitionRegistry definitionRegistry;

    /** 执行模板格式对应的完整打印管线。 */
    private final PrintEngine engine;

    /** 启动阶段冻结的默认请求配置。 */
    private final PrintRuntimeSettings settings;

    /** 接收不含业务数据的执行快照。 */
    private final PrintTelemetry telemetry;

    /**
     * 创建不持有请求状态的业务打印门面。
     *
     * @param repository 模板集合仓库
     * @param definitionRegistry 业务定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     */
    public PrintService(TemplateRepository repository, PrintDefinitionRegistry definitionRegistry,
                        PrintEngine engine, PrintRuntimeSettings settings) {
        this(repository, definitionRegistry, engine, settings, PrintTelemetry.NO_OP);
    }

    /**
     * 创建带安全观测端口的业务打印门面。
     *
     * @param repository 模板集合仓库
     * @param definitionRegistry 业务定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     * @param telemetry 不接收请求正文的观测端口
     */
    public PrintService(TemplateRepository repository, PrintDefinitionRegistry definitionRegistry,
                        PrintEngine engine, PrintRuntimeSettings settings, PrintTelemetry telemetry) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "definitionRegistry 不能为空");
        this.engine = Objects.requireNonNull(engine, "engine 不能为空");
        this.settings = Objects.requireNonNull(settings, "settings 不能为空");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry 不能为空");
    }

    /**
     * 使用当前激活模板集合生成 PDF。
     *
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @return 不可变 PDF 产物
     * @throws BaseException 模板、适配或打印链路拒绝请求时抛出
     */
    public PrintArtifact render(String definitionCode, Object request) {
        return observe(() -> {
            TemplateSet templateSet = repository.current()
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "当前没有已激活的模板集合"));
            return renderTemplateSet(templateSet, definitionCode, request);
        });
    }

    /**
     * 使用明确的历史模板集合版本生成 PDF。
     *
     * @param templateSetVersion 已发布模板集合版本
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @return 不可变 PDF 产物
     * @throws BaseException 模板、适配或打印链路拒绝请求时抛出
     */
    public PrintArtifact render(long templateSetVersion, String definitionCode, Object request) {
        return observe(() -> {
            TemplateSet templateSet = repository.find(templateSetVersion)
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "模板集合版本尚未发布：" + templateSetVersion));
            return renderTemplateSet(templateSet, definitionCode, request);
        });
    }

    /**
     * 从一次仓库读取的集合快照完成业务适配和请求构造。
     *
     * @param templateSet 当前请求锁定的模板集合
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @return 不可变 PDF 产物
     */
    private PrintArtifact renderTemplateSet(TemplateSet templateSet, String definitionCode, Object request) {
        PrintDefinition<?> definition = definitionRegistry.require(definitionCode);
        TemplateDefinition templateDefinition = templateSet.require(definition.templateCode());
        if (templateDefinition.type() != TemplateType.DOCUMENT) {
            throw PrintValidationException.invalidRequest("业务打印定义只能引用文档模板");
        }

        PrintContext context;
        try {
            context = definition.load(request);
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PrintAdapterException.executionFailed(exception);
        }
        if (context == null) {
            throw PrintValidationException.invalidRequest("业务打印数据适配器返回空上下文");
        }

        PrintRequest printRequest = new PrintRequest(templateDefinition.template(), context, OutputFormat.PDF,
                settings.locale(), settings.zoneId(), settings.renderOptions());
        return engine.render(printRequest);
    }

    /**
     * 记录一次完整调用，并保持原产物或异常不受观测实现影响。
     *
     * @param operation 待执行的同步打印调用
     * @return 原调用生成的打印产物
     */
    private PrintArtifact observe(RenderOperation operation) {
        long startedAt = System.nanoTime();
        try {
            PrintArtifact artifact = operation.render();
            long durationNanos = elapsedNanos(startedAt);
            int pageCount = pageCount(artifact);
            PrintExecutionSnapshot snapshot = PrintExecutionSnapshot.success(
                    artifact.outputFormat().value(), durationNanos, pageCount, artifact.contentLength());
            recordSafely(snapshot);
            return artifact;
        } catch (RuntimeException exception) {
            long durationNanos = elapsedNanos(startedAt);
            PrintFailureCategory failure = PrintFailureCategory.from(exception);
            recordSafely(PrintExecutionSnapshot.failure(OutputFormat.PDF.value(), failure, durationNanos));
            throw exception;
        }
    }

    /**
     * 观测实现属于旁路能力，不能覆盖打印主链路结果。
     *
     * @param snapshot 已脱敏的执行快照
     */
    private void recordSafely(PrintExecutionSnapshot snapshot) {
        try {
            telemetry.record(snapshot);
        } catch (RuntimeException ignored) {
            // 观测故障留给宿主监控处理，打印结果继续按原路径返回。
        }
    }

    /**
     * 从安全元数据读取页数，缺失或非法内容按零处理。
     *
     * @param artifact 已完成校验的打印产物
     * @return 非负页数
     */
    private int pageCount(PrintArtifact artifact) {
        String value = artifact.metadata().get("pageCount");
        if (value == null) {
            return 0;
        }
        try {
            int pageCount = Integer.parseInt(value);
            return Math.max(pageCount, 0);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * 纳秒时钟在极短调用中也返回可观察的正耗时。
     *
     * @param startedAt 调用开始时的纳秒时钟值
     * @return 至少为一纳秒的耗时
     */
    private long elapsedNanos(long startedAt) {
        return Math.max(System.nanoTime() - startedAt, 1L);
    }

    /** 延迟执行仓储、适配和渲染的完整同步调用。 */
    @FunctionalInterface
    private interface RenderOperation {

        /** @return 打印产物 */
        PrintArtifact render();
    }
}
