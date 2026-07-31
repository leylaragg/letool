package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.spel.SpelException;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ConcurrentLruCache;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Spring 表达式语言工具类。
 *
 * <p>该工具统一提供普通表达式、模板表达式、方法参数表达式及只读安全表达式的求值能力。
 * 常用表达式会被缓存，避免高频业务调用时重复解析。</p>
 *
 * <p>普通求值使用 Spring 完整表达式能力，只适合可信表达式。表达式内容来自请求参数、
 * 数据库或其他不可信来源时，应使用 {@link #evalSafe(String, Object, Class)}。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class SpelUtil {

    /**
     * 普通表达式缓存容量。
     */
    private static final int EXPRESSION_CACHE_CAPACITY = 256;

    /**
     * 模板表达式缓存容量。
     */
    private static final int TEMPLATE_CACHE_CAPACITY = 64;

    /**
     * Spring 表达式解析器。
     */
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * Spring 模板表达式上下文。
     */
    private static final TemplateParserContext TEMPLATE_PARSER_CONTEXT = new TemplateParserContext();

    /**
     * 方法参数名称发现器。
     */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    /**
     * 普通表达式缓存。
     */
    private static final ConcurrentLruCache<String, Expression> EXPRESSION_CACHE =
            new ConcurrentLruCache<>(EXPRESSION_CACHE_CAPACITY, SpelUtil::parseExpression);

    /**
     * 模板表达式缓存。
     */
    private static final ConcurrentLruCache<String, Expression> TEMPLATE_CACHE =
            new ConcurrentLruCache<>(TEMPLATE_CACHE_CAPACITY, SpelUtil::parseTemplate);

    /**
     * 工具类不允许实例化。
     */
    private SpelUtil() {
    }

    /**
     * 使用根对象和变量计算表达式。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param variables  变量集合，可以为空
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析或计算失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T eval(String expression, Object rootObject, Map<String, ?> variables) {
        return (T) eval(expression, rootObject, variables, Object.class);
    }

    /**
     * 使用根对象计算表达式。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析或计算失败时抛出
     */
    public static <T> T eval(String expression, Object rootObject) {
        return eval(expression, rootObject, (Map<String, ?>) null);
    }

    /**
     * 使用根对象和变量计算表达式，并转换为指定类型。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param variables  变量集合，可以为空
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static <T> T eval(String expression,
                             Object rootObject,
                             Map<String, ?> variables,
                             Class<T> resultType) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (rootObject != null) {
            context.setRootObject(rootObject);
        }
        setVariables(context, variables);
        return evaluate(getExpression(expression), context, resultType);
    }

    /**
     * 使用根对象计算表达式，并转换为指定类型。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static <T> T evalAs(String expression, Object rootObject, Class<T> resultType) {
        return eval(expression, rootObject, null, resultType);
    }

    /**
     * 使用变量计算模板表达式。
     *
     * <p>模板中的表达式需要使用 {@code #{...}} 包裹，例如
     * {@code 订单号：#{#orderId}}。</p>
     *
     * @param template  模板表达式
     * @param variables 变量集合，可以为空
     * @return 模板计算结果
     * @throws SpelException 模板解析或计算失败时抛出
     */
    public static String evalTemplate(String template, Map<String, ?> variables) {
        return evalTemplate(template, null, variables);
    }

    /**
     * 使用根对象和变量计算模板表达式。
     *
     * @param template   模板表达式
     * @param rootObject 根对象，可以为空
     * @param variables  变量集合，可以为空
     * @return 模板计算结果
     * @throws SpelException 模板解析或计算失败时抛出
     */
    public static String evalTemplate(String template, Object rootObject, Map<String, ?> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (rootObject != null) {
            context.setRootObject(rootObject);
        }
        setVariables(context, variables);
        return evaluate(getTemplate(template), context, String.class);
    }

    /**
     * 在方法调用上下文中计算表达式。
     *
     * <p>表达式可访问参数名、{@code #p0}、{@code #a0}，
     * 以及 {@code #target}、{@code #method}、{@code #args} 等变量。</p>
     *
     * @param expression 表达式
     * @param target     方法所属对象
     * @param method     被调用的方法
     * @param arguments  方法参数
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 方法上下文无效或表达式计算失败时抛出
     */
    public static <T> T evalMethod(String expression,
                                   Object target,
                                   Method method,
                                   Object[] arguments,
                                   Class<T> resultType) {
        if (method == null) {
            throw SpelException.evaluationFailed(
                    new IllegalArgumentException("方法不能为空"));
        }
        Object[] actualArguments = arguments == null ? new Object[0] : arguments;
        if (method.getParameterCount() != actualArguments.length) {
            throw SpelException.evaluationFailed(
                    new IllegalArgumentException("方法参数数量与实际参数数量不一致"));
        }

        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target, method, actualArguments, PARAMETER_NAME_DISCOVERER);
        context.setVariable("target", target);
        context.setVariable("method", method);
        context.setVariable("args", actualArguments);
        return evaluate(getExpression(expression), context, resultType);
    }

    /**
     * 使用只读数据绑定上下文计算表达式。
     *
     * <p>安全模式仅支持读取属性、集合索引及变量等常用能力，
     * 不允许类型引用、构造器调用、Bean 引用和任意实例方法调用。</p>
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static <T> T evalSafe(String expression, Object rootObject, Class<T> resultType) {
        return evalSafe(expression, rootObject, null, resultType);
    }

    /**
     * 使用只读数据绑定上下文和变量计算表达式。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param variables  变量集合，可以为空
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static <T> T evalSafe(String expression,
                                 Object rootObject,
                                 Map<String, ?> variables,
                                 Class<T> resultType) {
        SimpleEvaluationContext.Builder builder = SimpleEvaluationContext.forReadOnlyDataBinding();
        if (rootObject != null) {
            builder.withRootObject(rootObject);
        }
        SimpleEvaluationContext context = builder.build();
        setVariables(context, variables);
        return evaluate(getExpression(expression), context, resultType);
    }

    /**
     * 判断表达式是否成立。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @return 表达式结果为真时返回 {@code true}
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static boolean match(String expression, Object rootObject) {
        return match(expression, rootObject, null);
    }

    /**
     * 使用根对象和变量判断表达式是否成立。
     *
     * @param expression 表达式
     * @param rootObject 根对象，可以为空
     * @param variables  变量集合，可以为空
     * @return 表达式结果为真时返回 {@code true}
     * @throws SpelException 表达式解析、计算或类型转换失败时抛出
     */
    public static boolean match(String expression, Object rootObject, Map<String, ?> variables) {
        Boolean result = eval(expression, rootObject, variables, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取已缓存的普通表达式。
     *
     * @param expression 表达式
     * @return 已解析表达式
     */
    private static Expression getExpression(String expression) {
        validateExpression(expression);
        return EXPRESSION_CACHE.get(expression);
    }

    /**
     * 获取已缓存的模板表达式。
     *
     * @param template 模板表达式
     * @return 已解析模板表达式
     */
    private static Expression getTemplate(String template) {
        validateExpression(template);
        return TEMPLATE_CACHE.get(template);
    }

    /**
     * 解析普通表达式。
     *
     * @param expression 表达式
     * @return 已解析表达式
     */
    private static Expression parseExpression(String expression) {
        try {
            return EXPRESSION_PARSER.parseExpression(expression);
        } catch (RuntimeException exception) {
            throw SpelException.parseFailed(exception);
        }
    }

    /**
     * 解析模板表达式。
     *
     * @param template 模板表达式
     * @return 已解析模板表达式
     */
    private static Expression parseTemplate(String template) {
        try {
            return EXPRESSION_PARSER.parseExpression(template, TEMPLATE_PARSER_CONTEXT);
        } catch (RuntimeException exception) {
            throw SpelException.parseFailed(exception);
        }
    }

    /**
     * 执行表达式并转换结果类型。
     *
     * @param expression 已解析表达式
     * @param context    求值上下文
     * @param resultType 结果类型
     * @param <T>        返回值类型
     * @return 表达式计算结果
     */
    private static <T> T evaluate(Expression expression,
                                  EvaluationContext context,
                                  Class<T> resultType) {
        try {
            return expression.getValue(context, Objects.requireNonNull(resultType, "结果类型不能为空"));
        } catch (SpelException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw SpelException.evaluationFailed(exception);
        }
    }

    /**
     * 设置表达式变量。
     *
     * @param context   求值上下文
     * @param variables 变量集合，可以为空
     */
    private static void setVariables(EvaluationContext context, Map<String, ?> variables) {
        if (variables != null && !variables.isEmpty()) {
            variables.forEach(context::setVariable);
        }
    }

    /**
     * 校验表达式文本。
     *
     * @param expression 表达式文本
     */
    private static void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw SpelException.parseFailed(new IllegalArgumentException("表达式不能为空"));
        }
    }
}
