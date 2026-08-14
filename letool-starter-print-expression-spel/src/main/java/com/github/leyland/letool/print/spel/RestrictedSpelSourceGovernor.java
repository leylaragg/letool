package com.github.leyland.letool.print.spel;

/**
 * 在 Spring 解析器执行前治理表达式正文的递归结构容量。
 *
 * <p>该治理器只识别字符串边界和可能驱动递归解析的结构，不判断业务语法是否允许。完整语法安全边界仍由
 * {@link RestrictedSpelAstValidator} 的精确 AST 白名单负责。实例保存一次线性扫描状态，由单次编译独占，
 * 不跨表达式或线程复用。</p>
 *
 * @author leyland
 */
final class RestrictedSpelSourceGovernor {

    /** 解析器允许进入的圆括号、方括号和花括号累计嵌套深度。 */
    private static final int MAX_DELIMITER_DEPTH = 32;

    /** 允许连续出现的一元非运算符数量，与 AST 深度上限保持一致。 */
    private static final int MAX_PREFIX_NOT_OPERATORS = 31;

    /** 允许进入 Spring 解析器的条件运算符数量。 */
    private static final int MAX_CONDITIONAL_OPERATORS = 32;

    /** 用于保存当前未闭合分隔符的固定容量栈。 */
    private final char[] delimiters = new char[MAX_DELIMITER_DEPTH];

    /** 当前未闭合分隔符数量。 */
    private int delimiterDepth;

    /** 当前连续一元非运算符数量。 */
    private int prefixNotOperators;

    /** 当前条件运算符累计数量。 */
    private int conditionalOperators;

    /** 当前字符串引号；未进入字符串时为零字符。 */
    private char quote;

    /**
     * 创建单次编译使用的解析前容量治理器。
     */
    RestrictedSpelSourceGovernor() {
    }

    /**
     * 在线性扫描中校验解析递归结构不会超过安全容量。
     *
     * <p>治理状态归当前实例所有；调用方应为每次编译创建新实例，避免跨表达式共享扫描状态。</p>
     *
     * @param source 已通过总字符数校验的表达式正文
     * @throws IllegalArgumentException 分隔符、连续前缀运算符或条件运算符超过安全容量时抛出
     * @throws NullPointerException 正文为空时抛出
     */
    void validate(String source) {
        if (source == null) {
            throw new NullPointerException("source 不能为空");
        }
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quote != 0) {
                index = consumeQuotedCharacter(source, index, current);
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                prefixNotOperators = 0;
                continue;
            }
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (isOpeningDelimiter(current)) {
                pushDelimiter(current);
                prefixNotOperators = 0;
                continue;
            }
            if (isClosingDelimiter(current)) {
                popDelimiter(current);
                prefixNotOperators = 0;
                continue;
            }
            if (current == '!' && !isFollowedByEquals(source, index)) {
                enterPrefixNot();
                continue;
            }
            prefixNotOperators = 0;
            if (current == '?') {
                enterConditionalOperator();
            }
        }
        if (quote != 0 || delimiterDepth != 0) {
            throw invalidStructure();
        }
    }

    /**
     * 消费字符串内部字符，并正确处理 SpEL 使用的双引号转义形式。
     *
     * @param source 表达式正文
     * @param index 当前字符下标
     * @param current 当前字符
     * @return 本轮扫描完成后的字符下标；双引号转义时会跳过第二个引号
     */
    private int consumeQuotedCharacter(
            String source, int index, char current) {
        if (current != quote) {
            return index;
        }
        if (index + 1 < source.length()
                && source.charAt(index + 1) == quote) {
            return index + 1;
        }
        quote = 0;
        return index;
    }

    /**
     * 判断字符是否为需要治理深度的左分隔符。
     *
     * @param value 待判断字符
     * @return 属于圆括号、方括号或花括号左侧时返回 {@code true}
     */
    private boolean isOpeningDelimiter(char value) {
        return value == '(' || value == '[' || value == '{';
    }

    /**
     * 判断字符是否为需要治理深度的右分隔符。
     *
     * @param value 待判断字符
     * @return 属于圆括号、方括号或花括号右侧时返回 {@code true}
     */
    private boolean isClosingDelimiter(char value) {
        return value == ')' || value == ']' || value == '}';
    }

    /**
     * 将左分隔符压入固定容量栈，并在修改状态前检查深度上限。
     *
     * @param delimiter 待压入的左分隔符
     * @throws IllegalArgumentException 嵌套深度达到安全上限时抛出
     */
    private void pushDelimiter(char delimiter) {
        if (delimiterDepth >= MAX_DELIMITER_DEPTH) {
            throw invalidStructure();
        }
        delimiters[delimiterDepth] = delimiter;
        delimiterDepth++;
    }

    /**
     * 匹配并移除最近一个左分隔符。
     *
     * @param closing 待读取的右分隔符
     * @throws IllegalArgumentException 分隔符缺失或类型不匹配时抛出
     */
    private void popDelimiter(char closing) {
        if (delimiterDepth == 0
                || !matches(delimiters[delimiterDepth - 1], closing)) {
            throw invalidStructure();
        }
        delimiterDepth--;
    }

    /**
     * 判断一对左右分隔符是否属于同一类型。
     *
     * @param opening 左分隔符
     * @param closing 右分隔符
     * @return 分隔符类型匹配时返回 {@code true}
     */
    private boolean matches(char opening, char closing) {
        return opening == '(' && closing == ')'
                || opening == '[' && closing == ']'
                || opening == '{' && closing == '}';
    }

    /**
     * 判断当前感叹号是否属于不等运算符的一部分。
     *
     * @param source 表达式正文
     * @param index 当前感叹号下标
     * @return 后继字符为等号时返回 {@code true}
     */
    private boolean isFollowedByEquals(String source, int index) {
        return index + 1 < source.length() && source.charAt(index + 1) == '=';
    }

    /**
     * 记录连续一元非运算符，并在修改状态前检查安全上限。
     *
     * @throws IllegalArgumentException 连续一元非运算符超过安全上限时抛出
     */
    private void enterPrefixNot() {
        if (prefixNotOperators >= MAX_PREFIX_NOT_OPERATORS) {
            throw invalidStructure();
        }
        prefixNotOperators++;
    }

    /**
     * 记录条件运算符，并在修改状态前检查递归解析上限。
     *
     * @throws IllegalArgumentException 条件运算符累计数量超过安全上限时抛出
     */
    private void enterConditionalOperator() {
        if (conditionalOperators >= MAX_CONDITIONAL_OPERATORS) {
            throw invalidStructure();
        }
        conditionalOperators++;
    }

    /**
     * 创建不包含表达式正文、字符位置或解析器信息的内部容量异常。
     *
     * @return 安全的结构容量异常
     */
    private IllegalArgumentException invalidStructure() {
        return new IllegalArgumentException("条件表达式结构超过安全限制");
    }
}
