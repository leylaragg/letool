package io.github.leylaragg.letool.print.xml.expression;

import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 编译后可并发复用的条件表达式计划。
 *
 * @author leyland
 */
public interface PrintExpressionPlan {

    /**
     * 使用当前只读数据视图求值。
     *
     * @param context 求值上下文
     * @return 条件是否成立
     */
    boolean evaluate(ExpressionEvaluationContext context);

    /**
     * 声明表达式静态读取的数据路径。
     *
     * @return 不包含表达式正文的检查贡献
     */
    TemplateInspectionContribution inspectionContribution();

    /**
     * 用静态路径贡献和求值函数创建常见表达式计划。
     *
     * @param contribution 表达式读取的数据路径
     * @param evaluator 单次求值函数
     * @return 可并发复用的表达式计划
     */
    static PrintExpressionPlan of(
            TemplateInspectionContribution contribution,
            Predicate<ExpressionEvaluationContext> evaluator) {
        Objects.requireNonNull(contribution, "contribution 不能为空");
        Objects.requireNonNull(evaluator, "evaluator 不能为空");
        return new PrintExpressionPlan() {
            @Override
            public boolean evaluate(ExpressionEvaluationContext context) {
                return evaluator.test(context);
            }

            @Override
            public TemplateInspectionContribution inspectionContribution() {
                return contribution;
            }
        };
    }
}
