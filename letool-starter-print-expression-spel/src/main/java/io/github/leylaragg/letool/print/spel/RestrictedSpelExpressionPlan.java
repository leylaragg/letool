package io.github.leylaragg.letool.print.spel;

import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.xml.expression.ExpressionEvaluationContext;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import io.github.leylaragg.letool.print.template.inspection.TemplateInspectionContribution;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 保存已解析 SpEL 的不可变求值计划。
 *
 * <p>每次求值都会创建独立的数据根和 Spring 上下文，计划中没有跨请求共享的可变业务状态。</p>
 *
 * @author leyland
 */
final class RestrictedSpelExpressionPlan implements PrintExpressionPlan {

    /** 模板编译阶段生成且后续只读的 Spring 表达式。 */
    private final SpelExpression expression;

    /** 为每次绑定创建隔离预算的不可变工厂引用。 */
    private final Supplier<RestrictedSpelBudget> budgetFactory;

    /** 编译期从白名单 AST 提取的静态读取路径。 */
    private final TemplateInspectionContribution inspectionContribution;

    /**
     * 创建受限表达式计划。
     *
     * @param expression 已解析的只读表达式
     * @param budgetFactory 单次求值预算工厂
     * @param inspectionContribution 不含表达式正文的静态读取声明
     * @throws NullPointerException 参数为空时抛出
     */
    RestrictedSpelExpressionPlan(
            SpelExpression expression,
            Supplier<RestrictedSpelBudget> budgetFactory,
            TemplateInspectionContribution inspectionContribution) {
        this.expression = Objects.requireNonNull(expression, "expression 不能为空");
        this.budgetFactory = Objects.requireNonNull(
                budgetFactory, "budgetFactory 不能为空");
        this.inspectionContribution = Objects.requireNonNull(
                inspectionContribution, "inspectionContribution 不能为空");
    }

    /** @return 受限 AST 中实际读取的静态路径 */
    @Override
    public TemplateInspectionContribution inspectionContribution() {
        return inspectionContribution;
    }

    /**
     * 使用当前只读打印数据求值，并严格要求布尔结果。
     *
     * @param context 当前表达式求值上下文
     * @return 条件是否成立
     * @throws PrintValidationException 数据路径非法、求值失败或结果不是布尔值时抛出
     */
    @Override
    public boolean evaluate(ExpressionEvaluationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        try {
            RestrictedSpelBudget budget = Objects.requireNonNull(
                    budgetFactory.get(), "预算工厂不能返回 null");
            budget.checkpoint();
            // 每次调用从防御性数据视图创建独立根节点，避免并发绑定共享作用域状态。
            RestrictedSpelDataNode root = RestrictedSpelDataNode.from(
                    context.data(), budget);
            SimpleEvaluationContext evaluationContext = SimpleEvaluationContext
                    .forPropertyAccessors(new RestrictedSpelPropertyAccessor())
                    .withIndexAccessors(new RestrictedSpelIndexAccessor())
                    .withAssignmentDisabled()
                    .withRootObject(root)
                    .build();
            Object result = expression.getValue(evaluationContext, root);
            budget.checkpoint();
            if (!(result instanceof Boolean booleanResult)) {
                throw PrintValidationException.invalidDocument(
                        "条件表达式结果必须为布尔值");
            }
            return booleanResult;
        } catch (PrintValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Spring、预算和数据转换异常都可能携带属性名、类型或业务值，公开错误只保留稳定分类。
            throw PrintValidationException.invalidDocument("条件表达式求值失败");
        }
    }
}
