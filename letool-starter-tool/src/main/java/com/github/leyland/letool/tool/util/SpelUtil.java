package com.github.leyland.letool.tool.util;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * SpEL（Spring Expression Language）表达式工具——表达式求值、模板解析、条件匹配.
 *
 * <h3>三种核心能力</h3>
 * <table>
 *   <tr><th>方法</th><th>功能</th><th>典型场景</th></tr>
 *   <tr><td>{@link #eval(String, Object, Map)}</td><td>表达式求值</td><td>动态规则、配置解析</td></tr>
 *   <tr><td>{@link #evalTemplate(String, Map)}</td><td>模板占位符替换</td><td>消息模板、通知内容</td></tr>
 *   <tr><td>{@link #match(String, Object)}</td><td>条件匹配</td><td>动态条件、规则引擎</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 表达式求值：读取对象属性
 * String name = SpelUtil.eval("name", user);                    // → user.getName()
 * int age = SpelUtil.eval("age + 1", user);                     // → user.getAge() + 1
 *
 * // 带变量的表达式
 * int result = SpelUtil.eval("#a + #b", null,
 *     Map.of("a", 10, "b", 20));                                // → 30
 *
 * // 模板替换（使用 #{} 占位符，非 SpEL 标准语法，为便捷工具）
 * String msg = SpelUtil.evalTemplate("Hello #{name}!",
 *     Map.of("name", "World"));                                 // → "Hello World!"
 *
 * // 条件匹配
 * boolean adult = SpelUtil.match("age >= 18", user);            // → true/false
 * }</pre>
 */
public final class SpelUtil {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private SpelUtil() {}

    /**
     * 对表达式求值.
     *
     * <p>表达式中的 {@code #varName} 引用变量，直接属性名引用根对象的属性.</p>
     *
     * @param expression  SpEL 表达式字符串
     * @param rootObject  根对象（表达式直接引用其属性），可为 {@code null}
     * @param variables   变量映射（表达式通过 {@code #varName} 引用），可为 {@code null}
     * @param <T>         返回值类型
     * @return 表达式计算结果
     */
    @SuppressWarnings("unchecked")
    public static <T> T eval(String expression, Object rootObject, Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return (T) PARSER.parseExpression(expression).getValue(context);
    }

    /**
     * 对表达式求值（无变量）.
     *
     * @param expression  SpEL 表达式
     * @param rootObject  根对象
     * @param <T>         返回值类型
     * @return 表达式计算结果
     */
    public static <T> T eval(String expression, Object rootObject) {
        return eval(expression, rootObject, null);
    }

    /**
     * 模板字符串占位符替换.
     *
     * <p>查找 {@code #{key}} 形式的占位符，替换为 {@code variables} 中对应值的字符串表示.
     * 注意：这不是 SpEL 语法，而是简单的字符串替换，参考了 SLF4J 的占位符风格.</p>
     *
     * @param template  模板字符串（包含 {@code #{key}} 占位符）
     * @param variables 变量映射
     * @return 替换后的字符串，{@code template} 为 {@code null} 返回 {@code null}
     */
    public static String evalTemplate(String template, Map<String, Object> variables) {
        if (template == null) return null;
        if (variables == null || variables.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("#{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 评估表达式是否为 true——常用于动态条件判断.
     *
     * <p>表达式必须为 boolean 类型，如 {@code "age >= 18 && status == 1"}.</p>
     *
     * @param expression  SpEL 表达式（必须返回 boolean）
     * @param rootObject  根对象
     * @return {@code true} 如果表达式结果为 {@link Boolean#TRUE}
     */
    public static boolean match(String expression, Object rootObject) {
        return Boolean.TRUE.equals(eval(expression, rootObject, null));
    }
}
