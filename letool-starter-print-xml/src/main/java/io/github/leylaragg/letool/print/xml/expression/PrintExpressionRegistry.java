package io.github.leylaragg.letool.print.xml.expression;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按语言名保存可信表达式提供方的不可变注册表。
 *
 * @author leyland
 */
public final class PrintExpressionRegistry {

    /** 表达式语言名称白名单。 */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    /** 保持注册顺序的不可变提供方索引。 */
    private final Map<String, PrintConditionExpression> expressions;

    /**
     * 创建表达式注册表快照。
     *
     * @param expressions 待注册提供方
     */
    public PrintExpressionRegistry(Collection<? extends PrintConditionExpression> expressions) {
        Objects.requireNonNull(expressions, "expressions 不能为空");
        Map<String, PrintConditionExpression> snapshot = new LinkedHashMap<>();
        for (PrintConditionExpression expression : expressions) {
            if (expression == null) {
                throw new IllegalArgumentException("表达式提供方不能为 null");
            }
            String language = expression.language();
            if (language == null || !NAME.matcher(language).matches()) {
                throw new IllegalArgumentException("表达式语言名称不合法");
            }
            PrintConditionExpression frozen = new FrozenExpression(language, expression);
            if (snapshot.putIfAbsent(language, frozen) != null) {
                throw new IllegalArgumentException("表达式语言名称重复：" + language);
            }
        }
        this.expressions = Collections.unmodifiableMap(snapshot);
    }

    /**
     * 查找必需表达式提供方。
     *
     * @param language 表达式语言名
     * @return 已注册提供方
     */
    public PrintConditionExpression require(String language) {
        PrintConditionExpression expression = expressions.get(language);
        if (expression == null) {
            throw new IllegalArgumentException("表达式语言不存在：" + language);
        }
        return expression;
    }

    /**
     * 返回不可修改的语言名视图。
     *
     * @return 保持注册顺序的语言名
     */
    public Set<String> languages() {
        return expressions.keySet();
    }

    /** 冻结语言名并只委托编译行为的表达式提供方快照。 */
    private static final class FrozenExpression implements PrintConditionExpression {

        /** 注册时冻结的语言名。 */
        private final String language;

        /** 可信提供方编译委托。 */
        private final PrintConditionExpression delegate;

        /** 创建表达式提供方元数据快照。 */
        private FrozenExpression(String language, PrintConditionExpression delegate) {
            this.language = language;
            this.delegate = delegate;
        }

        @Override
        public String language() {
            return language;
        }

        @Override
        public PrintExpressionPlan compile(ExpressionCompileContext context) {
            return delegate.compile(context);
        }
    }
}
