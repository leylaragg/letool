package io.github.leylaragg.letool.print.service;

import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintArtifact;
import io.github.leylaragg.letool.print.api.PrintEngine;
import io.github.leylaragg.letool.print.api.PrintRequest;
import io.github.leylaragg.letool.print.api.PrintResult;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintAdapterException;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSource;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateType;

import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 通过业务定义编码生成 PDF 的同步打印门面。
 *
 * <p>门面只负责锁定模板版本、调用数据适配器和构造通用请求，不进入业务查询或权限实现。</p>
 *
 * @author leyland
 */
public final class PrintService {

    /** 提供当前和历史模板集合快照。 */
    private final TemplateSource templateSource;

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
     * @param templateSource 模板集合只读来源
     * @param definitionRegistry 业务定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     */
    public PrintService(
            TemplateSource templateSource,
            PrintDefinitionRegistry definitionRegistry,
            PrintEngine engine,
            PrintRuntimeSettings settings) {
        this(templateSource, definitionRegistry, engine, settings, PrintTelemetry.NO_OP);
    }

    /**
     * 创建带安全观测端口的业务打印门面。
     *
     * @param templateSource 模板集合只读来源
     * @param definitionRegistry 业务定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     * @param telemetry 不接收请求正文的观测端口
     */
    public PrintService(
            TemplateSource templateSource,
            PrintDefinitionRegistry definitionRegistry,
            PrintEngine engine,
            PrintRuntimeSettings settings,
            PrintTelemetry telemetry) {
        this.templateSource = Objects.requireNonNull(templateSource, "templateSource 不能为空");
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
            TemplateSet templateSet = templateSource.current()
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "当前没有已激活的模板集合"));
            return renderTemplateSet(templateSet, definitionCode, request);
        }, this::successSnapshot);
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
            TemplateSet templateSet = templateSource.find(templateSetVersion)
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "模板集合版本尚未发布：" + templateSetVersion));
            return renderTemplateSet(templateSet, definitionCode, request);
        }, this::successSnapshot);
    }

    /**
     * 使用当前激活模板集合，把 PDF 直接写入调用方目标。
     *
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @param output 调用方拥有并负责关闭的输出流
     * @return 不保留正文的打印结果
     * @throws BaseException 模板、适配、渲染或写出失败时抛出
     */
    public PrintResult renderTo(String definitionCode, Object request, OutputStream output) {
        return observe(() -> {
            requireOutput(output);
            TemplateSet templateSet = templateSource.current()
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "当前没有已激活的模板集合"));
            return renderTemplateSetTo(templateSet, definitionCode, request, output);
        }, this::successSnapshot);
    }

    /**
     * 使用明确的模板集合版本，把 PDF 直接写入调用方目标。
     *
     * @param templateSetVersion 已发布模板集合版本
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @param output 调用方拥有并负责关闭的输出流
     * @return 不保留正文的打印结果
     * @throws BaseException 模板、适配、渲染或写出失败时抛出
     */
    public PrintResult renderTo(
            long templateSetVersion,
            String definitionCode,
            Object request,
            OutputStream output) {
        return observe(() -> {
            requireOutput(output);
            TemplateSet templateSet = templateSource.find(templateSetVersion)
                    .orElseThrow(() -> PrintValidationException.invalidRequest(
                            "模板集合版本尚未发布：" + templateSetVersion));
            return renderTemplateSetTo(templateSet, definitionCode, request, output);
        }, this::successSnapshot);
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
        return engine.render(createRequest(templateSet, definitionCode, request));
    }

    /** 使用同一请求构造流程执行流式打印。 */
    private PrintResult renderTemplateSetTo(
            TemplateSet templateSet,
            String definitionCode,
            Object request,
            OutputStream output) {
        return engine.renderTo(createRequest(templateSet, definitionCode, request), output);
    }

    /** 从锁定的模板集合完成业务适配并创建通用打印请求。 */
    private PrintRequest createRequest(
            TemplateSet templateSet,
            String definitionCode,
            Object request) {
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
        return printRequest;
    }

    /**
     * 记录一次完整调用，并保持原产物或异常不受观测实现影响。
     *
     * @param operation 待执行的同步打印调用
     * @param successFactory 把成功结果转换为安全快照的函数
     * @param <T> 内存产物或流式结果类型
     * @return 原调用生成的结果
     */
    private <T> T observe(
            Supplier<T> operation,
            BiFunction<T, Long, PrintExecutionSnapshot> successFactory) {
        long startedAt = System.nanoTime();
        try {
            T result = operation.get();
            long durationNanos = elapsedNanos(startedAt);
            recordSafely(successFactory.apply(result, durationNanos));
            return result;
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
     * @param metadata 已完成校验的安全元数据
     * @return 非负页数
     */
    private int pageCount(Map<String, String> metadata) {
        String value = metadata.get("pageCount");
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

    /** 把内存产物转换为统一成功快照。 */
    private PrintExecutionSnapshot successSnapshot(PrintArtifact artifact, long durationNanos) {
        return PrintExecutionSnapshot.success(
                artifact.outputFormat().value(), durationNanos,
                pageCount(artifact.metadata()), artifact.contentLength());
    }

    /** 把流式结果转换为统一成功快照。 */
    private PrintExecutionSnapshot successSnapshot(PrintResult result, long durationNanos) {
        return PrintExecutionSnapshot.success(
                result.outputFormat().value(), durationNanos,
                pageCount(result.metadata()), result.contentLength());
    }

    /** 空输出流不能进入业务适配或引擎调用。 */
    private void requireOutput(OutputStream output) {
        if (output == null) {
            throw PrintValidationException.invalidRequest("输出流不能为空");
        }
    }
}
