package com.github.leyland.letool.ruleengine.expression.lexer;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对阶段一表达式执行确定性单向字符扫描的 Lexer。
 *
 * <p>该类不查询事实或函数目录，不判断路径和时间文本的语义合法性。</p>
 */
public final class ExpressionLexer {

    /**
     * 将表达式源文本转换为 Token。
     *
     * <p>Token 数量限制包含 EOF。源码或 Token 超限属于用户输入资源诊断，
     * 不通过异常报告。</p>
     *
     * @param source 非空表达式源文本
     * @param limits 非空资源限制
     * @return 成功 Token 列表或单个阻断诊断
     * @throws RuleEngineException API 参数为空时抛出
     */
    public LexerResult tokenize(String source, EngineLimits limits) {
        if (source == null || limits == null) {
            throw RuleEngineException.invalidArgument();
        }
        if (source.length() > limits.getMaxSourceLength()) {
            return LexerResult.failure(diagnostic(
                    RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED,
                    0,
                    source.length(),
                    List.of()));
        }
        return new ScanSession(source, limits.getMaxTokens()).scan();
    }

    /**
     * 创建词法错误诊断。
     *
     * @param code 诊断码
     * @param start 起始位置
     * @param end 结束位置
     * @param arguments 安全参数
     * @return 词法错误诊断
     */
    private static RuleDiagnostic diagnostic(
            RuleDiagnosticCode code, int start, int end, List<Object> arguments) {
        return new RuleDiagnostic(
                code,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.LEXICAL,
                start,
                end,
                arguments,
                null);
    }

    /**
     * 单次 tokenize 调用私有的可变扫描会话。
     */
    private static final class ScanSession {

        /** 本次扫描的完整源码。 */
        private final String source;

        /** 包含末尾 EOF 的 Token 数量预算。 */
        private final int maxTokens;

        /** 按源码顺序累积的 Token。 */
        private final List<Token> tokens;

        /** 下一个待扫描的 UTF-16 偏移。 */
        private int position;

        /** 首个阻断词法诊断。 */
        private RuleDiagnostic failure;

        /**
         * 创建扫描会话。
         *
         * @param source 源文本
         * @param maxTokens 最大 Token 数，包含 EOF
         */
        private ScanSession(String source, int maxTokens) {
            this.source = source;
            this.maxTokens = maxTokens;
            this.tokens = new ArrayList<>(Math.min(maxTokens, 64));
        }

        /**
         * 扫描整个表达式。
         *
         * @return Lexer 结果
         */
        private LexerResult scan() {
            while (position < source.length() && failure == null) {
                while (position < source.length() && isWhitespace(source.charAt(position))) {
                    position++;
                }
                if (position >= source.length()) {
                    break;
                }
                if (tokens.size() >= maxTokens - 1) {
                    fail(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED,
                            position, position + 1, List.of());
                    break;
                }
                char current = source.charAt(position);
                if (current == '\'' || current == '"') {
                    scanString();
                } else if (current >= '0' && current <= '9') {
                    scanNumber();
                } else if (current == '$') {
                    scanDollarForm();
                } else if (isAsciiLetter(current)) {
                    scanWord();
                } else {
                    scanSymbol(current);
                }
            }
            if (failure != null) {
                return LexerResult.failure(failure);
            }
            if (tokens.size() >= maxTokens) {
                return LexerResult.failure(diagnostic(
                        RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED,
                        source.length(), source.length(), List.of()));
            }
            tokens.add(new Token(
                    TokenType.EOF, "", "", source.length(), source.length()));
            return LexerResult.success(tokens);
        }

        /**
         * 扫描受控转义字符串。
         */
        private void scanString() {
            int start = position;
            char quote = source.charAt(position++);
            StringBuilder decoded = new StringBuilder();
            while (position < source.length()) {
                char current = source.charAt(position++);
                if (current == quote) {
                    emit(TokenType.STRING, start, position, decoded.toString());
                    return;
                }
                if (current != '\\') {
                    decoded.append(current);
                    continue;
                }
                int escapeStart = position - 1;
                if (position >= source.length()) {
                    fail(RuleDiagnosticCode.INVALID_ESCAPE, escapeStart, position, List.of());
                    return;
                }
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '\\' -> decoded.append('\\');
                    case '\'' -> decoded.append('\'');
                    case '"' -> decoded.append('"');
                    case 'n' -> decoded.append('\n');
                    case 'r' -> decoded.append('\r');
                    case 't' -> decoded.append('\t');
                    default -> {
                        fail(RuleDiagnosticCode.INVALID_ESCAPE,
                                escapeStart, position, List.of());
                        return;
                    }
                }
            }
            fail(RuleDiagnosticCode.UNTERMINATED_STRING,
                    start, source.length(), List.of());
        }

        /**
         * 扫描非负数字文本；负号始终单独生成 Token。
         */
        private void scanNumber() {
            int start = position;
            while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                position++;
            }
            boolean decimal = position + 1 < source.length()
                    && source.charAt(position) == '.'
                    && isAsciiDigit(source.charAt(position + 1));
            if (decimal) {
                position++;
                while (position < source.length() && isAsciiDigit(source.charAt(position))) {
                    position++;
                }
            }
            String raw = source.substring(start, position);
            emit(decimal ? TokenType.DECIMAL : TokenType.INTEGER,
                    start, position, normalizeNumber(raw, decimal));
        }

        /**
         * 扫描事实路径或函数编码。
         */
        private void scanDollarForm() {
            int start = position;
            if (position + 1 < source.length() && source.charAt(position + 1) == '{') {
                position += 2;
                int contentStart = position;
                while (position < source.length() && source.charAt(position) != '}') {
                    position++;
                }
                if (position >= source.length()) {
                    fail(RuleDiagnosticCode.UNTERMINATED_PATH,
                            start, source.length(), List.of());
                    return;
                }
                String path = source.substring(contentStart, position);
                position++;
                emit(TokenType.PATH, start, position, path);
                return;
            }
            if (position + 1 >= source.length()
                    || !isAsciiLetter(source.charAt(position + 1))) {
                position++;
                fail(RuleDiagnosticCode.UNKNOWN_CHARACTER,
                        start, position, List.of("$"));
                return;
            }
            position += 2;
            while (position < source.length()
                    && isAsciiIdentifierPart(source.charAt(position))) {
                position++;
            }
            String code = source.substring(start + 1, position).toUpperCase(Locale.ROOT);
            emit(TokenType.FUNCTION, start, position, code);
        }

        /**
         * 扫描关键字或裸标识符。
         */
        private void scanWord() {
            int start = position;
            position = wordEnd(position);
            String keyword = source.substring(start, position).toUpperCase(Locale.ROOT);
            if ("NOT".equals(keyword)) {
                scanNotOrNotIn(start);
                return;
            }
            if ("IS".equals(keyword)) {
                scanNullPredicate(start);
                return;
            }
            TokenType type = switch (keyword) {
                case "TRUE", "FALSE" -> TokenType.BOOLEAN;
                case "NULL" -> TokenType.NULL;
                case "DATE" -> TokenType.DATE;
                case "DATETIME" -> TokenType.DATETIME;
                case "INSTANT" -> TokenType.INSTANT;
                case "AND" -> TokenType.AND;
                case "OR" -> TokenType.OR;
                case "IN" -> TokenType.IN;
                case "BETWEEN" -> TokenType.BETWEEN;
                default -> TokenType.IDENTIFIER;
            };
            String normalized = switch (type) {
                case BOOLEAN, NULL -> keyword.toLowerCase(Locale.ROOT);
                default -> keyword;
            };
            emit(type, start, position, normalized);
        }

        /**
         * 将 NOT 和后续 IN 合并；其他情况保持逻辑非。
         *
         * @param start NOT 起始位置
         */
        private void scanNotOrNotIn(int start) {
            int notEnd = position;
            int candidate = skipWhitespace(position);
            if (matchesWord(candidate, "IN")) {
                position = candidate + 2;
                emit(TokenType.NOT_IN, start, position, "NOT IN");
                return;
            }
            position = notEnd;
            emit(TokenType.NOT, start, position, "NOT");
        }

        /**
         * 将 IS NULL 和 IS NOT NULL 合并，拒绝孤立 IS。
         *
         * @param start IS 起始位置
         */
        private void scanNullPredicate(int start) {
            int isEnd = position;
            int candidate = skipWhitespace(position);
            if (matchesWord(candidate, "NULL")) {
                position = candidate + 4;
                emit(TokenType.IS_NULL, start, position, "IS NULL");
                return;
            }
            if (matchesWord(candidate, "NOT")) {
                int notEnd = candidate + 3;
                int nullStart = skipWhitespace(notEnd);
                if (matchesWord(nullStart, "NULL")) {
                    position = nullStart + 4;
                    emit(TokenType.IS_NOT_NULL, start, position, "IS NOT NULL");
                    return;
                }
                position = notEnd;
                fail(RuleDiagnosticCode.UNEXPECTED_TOKEN,
                        start, position, List.of("IS NOT"));
                return;
            }
            position = isEnd;
            fail(RuleDiagnosticCode.UNEXPECTED_TOKEN,
                    start, position, List.of("IS"));
        }

        /**
         * 扫描运算符和分隔符。
         *
         * @param current 当前字符
         */
        private void scanSymbol(char current) {
            int start = position++;
            switch (current) {
                case '=' -> emit(TokenType.EQ, start, position, "=");
                case '!' -> emitIfFollowedByEquals(TokenType.NE, start, "!=");
                case '>' -> emitWithOptionalEquals(TokenType.GT, TokenType.GE, start);
                case '<' -> emitWithOptionalEquals(TokenType.LT, TokenType.LE, start);
                case '+' -> emit(TokenType.PLUS, start, position, "+");
                case '-' -> emit(TokenType.MINUS, start, position, "-");
                case '*' -> emit(TokenType.MULTIPLY, start, position, "*");
                case '/' -> emit(TokenType.DIVIDE, start, position, "/");
                case '%' -> emit(TokenType.MODULO, start, position, "%");
                case '(' -> emit(TokenType.LPAREN, start, position, "(");
                case ')' -> emit(TokenType.RPAREN, start, position, ")");
                case ',' -> emit(TokenType.COMMA, start, position, ",");
                default -> failUnknownCodePoint(start, current);
            }
        }

        /**
         * 按完整 UTF-16 码点消费未知字符，并安全编码孤立代理项。
         *
         * @param start 起始位置
         * @param current 当前 UTF-16 代码单元
         */
        private void failUnknownCodePoint(int start, char current) {
            if (Character.isHighSurrogate(current)
                    && position < source.length()
                    && Character.isLowSurrogate(source.charAt(position))) {
                position++;
                fail(RuleDiagnosticCode.UNKNOWN_CHARACTER,
                        start, position, List.of(source.substring(start, position)));
                return;
            }
            String argument = Character.isSurrogate(current)
                    ? String.format(Locale.ROOT, "U+%04X", (int) current)
                    : String.valueOf(current);
            fail(RuleDiagnosticCode.UNKNOWN_CHARACTER,
                    start, position, List.of(argument));
        }

        /**
         * 仅当后续为等号时生成双字符操作符。
         *
         * @param type 双字符类型
         * @param start 起始位置
         * @param normalized 规范文本
         */
        private void emitIfFollowedByEquals(TokenType type, int start, String normalized) {
            if (position < source.length() && source.charAt(position) == '=') {
                position++;
                emit(type, start, position, normalized);
            } else {
                fail(RuleDiagnosticCode.UNKNOWN_CHARACTER,
                        start, position, List.of("!"));
            }
        }

        /**
         * 生成可带等号的比较操作符。
         *
         * @param plainType 单字符类型
         * @param equalsType 双字符类型
         * @param start 起始位置
         */
        private void emitWithOptionalEquals(
                TokenType plainType, TokenType equalsType, int start) {
            if (position < source.length() && source.charAt(position) == '=') {
                position++;
                emit(equalsType, start, position, source.substring(start, position));
            } else {
                emit(plainType, start, position, source.substring(start, position));
            }
        }

        /**
         * 在保留 EOF 预算的前提下添加普通 Token。
         *
         * @param type 类型
         * @param start 起始位置
         * @param end 结束位置
         * @param normalized 规范值
         */
        private void emit(TokenType type, int start, int end, String normalized) {
            if (tokens.size() >= maxTokens - 1) {
                fail(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED,
                        start, end, List.of());
                return;
            }
            tokens.add(new Token(
                    type, source.substring(start, end), normalized, start, end));
        }

        /**
         * 保存第一个阻断诊断。
         *
         * @param code 诊断码
         * @param start 起始位置
         * @param end 结束位置
         * @param arguments 安全参数
         */
        private void fail(
                RuleDiagnosticCode code, int start, int end, List<Object> arguments) {
            if (failure == null) {
                failure = diagnostic(code, start, end, arguments);
            }
        }

        /**
         * 查找 ASCII 标识符结尾。
         *
         * @param start 起始位置
         * @return 结束位置
         */
        private int wordEnd(int start) {
            int end = start;
            while (end < source.length() && isAsciiIdentifierPart(source.charAt(end))) {
                end++;
            }
            return end;
        }

        /**
         * 跳过普通 ASCII 空白。
         *
         * @param start 起始位置
         * @return 第一个非空白位置
         */
        private int skipWhitespace(int start) {
            int end = start;
            while (end < source.length() && isWhitespace(source.charAt(end))) {
                end++;
            }
            return end;
        }

        /**
         * 在指定位置匹配完整 ASCII 关键字。
         *
         * @param start 起始位置
         * @param keyword 大写关键字
         * @return 完整匹配时返回 {@code true}
         */
        private boolean matchesWord(int start, String keyword) {
            if (start + keyword.length() > source.length()) {
                return false;
            }
            for (int offset = 0; offset < keyword.length(); offset++) {
                char actual = source.charAt(start + offset);
                char expected = keyword.charAt(offset);
                if (actual != expected && actual != expected + ('a' - 'A')) {
                    return false;
                }
            }
            int end = start + keyword.length();
            return end == source.length() || !isAsciiIdentifierPart(source.charAt(end));
        }
    }

    /**
     * 规范化数字文本而不依赖区域设置或任意精度对象分配。
     *
     * @param raw 原始数字文本
     * @param decimal 是否包含小数点
     * @return 规范数字文本
     */
    private static String normalizeNumber(String raw, boolean decimal) {
        int dot = decimal ? raw.indexOf('.') : raw.length();
        int integerStart = 0;
        while (integerStart < dot - 1 && raw.charAt(integerStart) == '0') {
            integerStart++;
        }
        String integer = raw.substring(integerStart, dot);
        if (!decimal) {
            return integer;
        }
        int fractionEnd = raw.length();
        while (fractionEnd > dot + 1 && raw.charAt(fractionEnd - 1) == '0') {
            fractionEnd--;
        }
        if (fractionEnd == dot + 1) {
            return integer;
        }
        return integer + raw.substring(dot, fractionEnd);
    }

    /**
     * 判断 ASCII 字母。
     *
     * @param character 字符
     * @return 是 ASCII 字母时返回 {@code true}
     */
    private static boolean isAsciiLetter(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z';
    }

    /**
     * 判断 ASCII 数字。
     *
     * @param character 字符
     * @return 是 ASCII 数字时返回 {@code true}
     */
    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /**
     * 判断 ASCII 标识符后续字符。
     *
     * @param character 字符
     * @return 是字母、数字或下划线时返回 {@code true}
     */
    private static boolean isAsciiIdentifierPart(char character) {
        return isAsciiLetter(character) || isAsciiDigit(character) || character == '_';
    }

    /**
     * 判断规则语法允许的普通空白。
     *
     * @param character 字符
     * @return 是空格、制表、换行、回车或换页时返回 {@code true}
     */
    private static boolean isWhitespace(char character) {
        return character == ' ' || character == '\t' || character == '\n'
                || character == '\r' || character == '\f';
    }
}
