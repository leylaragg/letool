package io.github.leylaragg.letool.print.template;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 编排模板集合构造、校验与发布。
 *
 * @author leyland
 */
public final class TemplateSetPublisher {

    /** 接收已校验集合的版本仓库。 */
    private final TemplateRepository repository;

    /** 构造发布器时冻结的校验器列表。 */
    private final List<TemplateSetValidator> validators;

    /** 负责集合结构校验和摘要计算。 */
    private final TemplateSetFactory factory;

    /**
     * 使用标准治理上限创建发布器。
     *
     * @param repository 模板仓库
     * @param validators 发布前校验器
     */
    public TemplateSetPublisher(
            TemplateRepository repository,
            Collection<? extends TemplateSetValidator> validators) {
        this(repository, validators, TemplateSetFactory.standard());
    }

    /** 包内测试可替换集合治理上限。 */
    TemplateSetPublisher(
            TemplateRepository repository,
            Collection<? extends TemplateSetValidator> validators,
            TemplateSetFactory factory) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        Objects.requireNonNull(validators, "validators 不能为空");
        List<TemplateSetValidator> snapshot = new ArrayList<>(validators.size());
        for (TemplateSetValidator validator : validators) {
            snapshot.add(Objects.requireNonNull(validator, "validator 不能为空"));
        }
        this.validators = List.copyOf(snapshot);
        this.factory = Objects.requireNonNull(factory, "factory 不能为空");
    }

    /**
     * 构造并发布新版本，不切换当前集合。
     *
     * @param version 集合版本
     * @param definitions 模板定义
     * @return 已发布集合
     */
    public TemplateSet publish(
            long version, Collection<TemplateDefinition> definitions) {
        return repository.publish(prepare(version, definitions));
    }

    /**
     * 构造新版本，并在校验通过后原子发布和激活。
     *
     * @param version 集合版本
     * @param definitions 模板定义
     * @return 已发布集合
     */
    public TemplateSet publishAndActivate(
            long version, Collection<TemplateDefinition> definitions) {
        return repository.publishAndActivate(prepare(version, definitions));
    }

    /** 完成集合构造和全部扩展校验后才交给仓库。 */
    private TemplateSet prepare(
            long version, Collection<TemplateDefinition> definitions) {
        TemplateSet candidate = factory.create(version, definitions);
        for (TemplateSetValidator validator : validators) {
            try {
                validator.validate(candidate);
            } catch (PrintValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw PrintValidationException.invalidRequest(
                        "模板集合发布校验失败");
            }
        }
        return candidate;
    }
}
