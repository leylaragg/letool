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

    /**
     * 创建不持有请求状态的业务打印门面。
     *
     * @param repository 模板集合仓库
     * @param definitionRegistry 业务定义注册表
     * @param engine 通用打印引擎
     * @param settings 不可变运行时配置
     */
    public PrintService(
            TemplateRepository repository,
            PrintDefinitionRegistry definitionRegistry,
            PrintEngine engine,
            PrintRuntimeSettings settings) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.definitionRegistry = Objects.requireNonNull(
                definitionRegistry, "definitionRegistry 不能为空");
        this.engine = Objects.requireNonNull(engine, "engine 不能为空");
        this.settings = Objects.requireNonNull(settings, "settings 不能为空");
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
        TemplateSet templateSet = repository.current()
                .orElseThrow(() -> PrintValidationException.invalidRequest(
                        "当前没有已激活的模板集合"));
        return render(templateSet, definitionCode, request);
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
    public PrintArtifact render(
            long templateSetVersion, String definitionCode, Object request) {
        TemplateSet templateSet = repository.find(templateSetVersion)
                .orElseThrow(() -> PrintValidationException.invalidRequest(
                        "模板集合版本尚未发布：" + templateSetVersion));
        return render(templateSet, definitionCode, request);
    }

    /**
     * 从一次仓库读取的集合快照完成业务适配和请求构造。
     *
     * @param templateSet 当前请求锁定的模板集合
     * @param definitionCode 业务打印定义编码
     * @param request 适配器声明的业务请求
     * @return 不可变 PDF 产物
     */
    private PrintArtifact render(
            TemplateSet templateSet, String definitionCode, Object request) {
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

        PrintRequest printRequest = new PrintRequest(
                templateDefinition.template(),
                context,
                OutputFormat.PDF,
                settings.locale(),
                settings.zoneId(),
                settings.renderOptions());
        return engine.render(printRequest);
    }
}
