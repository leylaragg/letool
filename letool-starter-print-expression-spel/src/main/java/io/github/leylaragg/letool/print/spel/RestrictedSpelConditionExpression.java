package io.github.leylaragg.letool.print.spel;

import io.github.leylaragg.letool.print.xml.PrintCompilationException;
import io.github.leylaragg.letool.print.xml.XmlDsl;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import io.github.leylaragg.letool.print.xml.expression.PrintConditionExpression;
import io.github.leylaragg.letool.print.xml.expression.PrintExpressionPlan;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 为动态打印 XML 提供显式注册的受限 SpEL 条件表达式。
 *
 * <p>实例本身不持有求值数据，可以安全地注册到不可变表达式注册表并并发编译模板。</p>
 *
 * @author leyland
 */
public final class RestrictedSpelConditionExpression implements PrintConditionExpression {

    /** 对外暴露的稳定表达式语言名称。 */
    private static final String LANGUAGE = "spel";

    /** 只负责把静态正文解析为 Spring 表达式树的无状态解析器。 */
    private final SpelExpressionParser parser;

    /** 按默认拒绝策略校验解析结果的无状态 AST 校验器。 */
    private final RestrictedSpelAstValidator astValidator;

    /** 为每次求值创建独立资源预算的工厂。 */
    private final Supplier<RestrictedSpelBudget> budgetFactory;

    /**
     * 使用安全默认配置创建受限 SpEL 提供方。
     */
    public RestrictedSpelConditionExpression() {
        this(RestrictedSpelBudget::standard);
    }

    /**
     * 使用指定预算工厂创建提供方。
     *
     * <p>构造器保持包级可见，只用于资源治理的确定性测试，不向宿主开放放宽安全限制的入口。</p>
     *
     * @param budgetFactory 单次求值预算工厂
     * @throws NullPointerException 预算工厂为空时抛出
     */
    RestrictedSpelConditionExpression(
            Supplier<RestrictedSpelBudget> budgetFactory) {
        this.parser = new SpelExpressionParser();
        this.astValidator = new RestrictedSpelAstValidator();
        this.budgetFactory = Objects.requireNonNull(
                budgetFactory, "budgetFactory 不能为空");
    }

    /**
     * 返回 XML 中显式声明的语言名称。
     *
     * @return 固定的小写 {@code spel}
     */
    @Override
    public String language() {
        return LANGUAGE;
    }

    /**
     * 编译静态条件正文，并生成不持有业务数据的不可变计划。
     *
     * @param context 表达式编译上下文
     * @return 可并发复用的表达式计划
     * @throws PrintCompilationException 表达式为空或无法解析时抛出
     * @throws NullPointerException 编译上下文为空时抛出
     */
    @Override
    public PrintExpressionPlan compile(ExpressionCompileContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        String source = context.expression();
        if (source.isBlank()
                || source.length() > XmlDsl.MAX_EXPRESSION_CHARACTERS) {
            throw compilationFailure(context.location());
        }
        try {
            // Spring 解析括号等结构时会递归调用解析方法，因此必须在交给解析器前限制源码嵌套深度。
            new RestrictedSpelSourceGovernor().validate(source);
            SpelExpression parsed = parser.parseRaw(source);
            // 解析成功不代表表达式安全，必须在生成可执行计划前完成整棵 AST 白名单校验。
            astValidator.validate(parsed.getAST());
            return new RestrictedSpelExpressionPlan(parsed, budgetFactory);
        } catch (RuntimeException exception) {
            // 解析器、源码治理和 AST 校验异常都可能携带正文或实现细节，统一转换为无原因链的安全异常。
            throw compilationFailure(context.location());
        }
    }

    /**
     * 创建不会回显表达式正文的统一编译异常。
     *
     * @param location 框架生成的安全模板位置
     * @return 受控打印编译异常
     */
    private PrintCompilationException compilationFailure(String location) {
        return PrintCompilationException.invalid("条件表达式编译失败：" + location);
    }
}
