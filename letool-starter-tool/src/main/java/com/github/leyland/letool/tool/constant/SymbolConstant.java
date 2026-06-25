package com.github.leyland.letool.tool.constant;

/**
 * 常用符号常量——避免代码中出现魔法字符串.
 *
 * <h3>设计意图</h3>
 * <p>Java 中直接写 {@code "."}、{@code ","} 等字符字面量时，其含义不够明确且容易写错
 * （如把逗号写成句号）。通过含义明确的常量名引用，提高可读性并减少笔误.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String path = "com" + SymbolConstant.DOT + "example" + SymbolConstant.DOT + "app";
 * String[] parts = str.split(SymbolConstant.COMMA);
 * }</pre>
 *
 * <p>注意：本类仅包含<em>单字符</em>符号常量，多字符分隔符（如 {@code \r\n}）请自行定义.</p>
 */
public final class SymbolConstant {

    /** 英文句号 */
    public static final String DOT = ".";
    /** 英文逗号 */
    public static final String COMMA = ",";
    /** 英文冒号 */
    public static final String COLON = ":";
    /** 英文分号 */
    public static final String SEMICOLON = ";";
    /** 正斜杠 */
    public static final String SLASH = "/";
    /** 反斜杠 */
    public static final String BACKSLASH = "\\";
    /** 下划线 */
    public static final String UNDERLINE = "_";
    /** 短横线 / 减号 */
    public static final String DASH = "-";
    /** 空字符串 */
    public static final String EMPTY = "";
    /** 空格 */
    public static final String SPACE = " ";
    /** 竖线 / 管道符 */
    public static final String PIPE = "|";
    /** 星号 / 通配符 */
    public static final String STAR = "*";
    /** 换行符 */
    public static final String NEWLINE = "\n";

    private SymbolConstant() {}
}
