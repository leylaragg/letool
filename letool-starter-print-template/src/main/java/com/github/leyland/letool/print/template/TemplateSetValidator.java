package com.github.leyland.letool.print.template;

/**
 * 模板集合发布前的扩展校验器。
 *
 * <p>实现由可信 Java 代码注册，并应支持并发调用。</p>
 *
 * @author leyland
 */
@FunctionalInterface
public interface TemplateSetValidator {

    /**
     * 校验不可变候选集合。
     *
     * @param candidate 待发布集合
     */
    void validate(TemplateSet candidate);
}
