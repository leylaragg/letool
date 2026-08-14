package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.template.TemplateSet;
import com.github.leyland.letool.print.template.TemplateSetValidator;

import java.util.Objects;

/**
 * 在模板集合发布前编译 XML 引用图，阻止无效集合进入运行期。
 *
 * @author leyland
 */
public final class XmlTemplateSetValidator implements TemplateSetValidator {

    /** 发布校验复用的无状态集合编译器。 */
    private final XmlTemplateSetCompiler compiler;

    /** 使用 XML 模块默认能力创建发布校验器。 */
    public XmlTemplateSetValidator() {
        this(new XmlTemplateSetCompiler());
    }

    /**
     * 使用宿主配置好的集合编译器执行发布校验。
     *
     * @param compiler XML 模板集合编译器
     */
    public XmlTemplateSetValidator(XmlTemplateSetCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler 不能为空");
    }

    /**
     * 编译候选集合，确保发布后不会留下无效引用图。
     *
     * @param candidate 待发布集合
     */
    @Override
    public void validate(TemplateSet candidate) {
        try {
            compiler.compile(candidate);
        } catch (PrintCompilationException exception) {
            // 编译异常只携带经过约束的定位信息，可以交给发布接口展示。
            throw PrintValidationException.invalidRequest("XML 模板集合校验失败：" + exception.detail());
        } catch (RuntimeException exception) {
            // 未知扩展异常可能包含业务数据，这里只保留稳定的校验结果。
            throw PrintValidationException.invalidRequest("XML 模板集合校验失败");
        }
    }
}
