package com.github.leyland.letool.print.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 编译完成的受限 JSON 数据路径。
 *
 * @author leyland
 */
final class CompiledDataPath {

    /** 对象字段段的安全格式。 */
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    /** 循环变量名的安全格式。 */
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 可选循环变量名；根路径为 {@code null}。 */
    private final String variableName;

    /** 不可变对象字段段。 */
    private final List<String> segments;

    /** 用于安全错误定位的规范路径。 */
    private final String displayPath;

    /** 创建不可变路径。 */
    private CompiledDataPath(String variableName, List<String> segments, String displayPath) {
        this.variableName = variableName;
        this.segments = List.copyOf(segments);
        this.displayPath = displayPath;
    }

    /**
     * 编译并校验数据路径。
     *
     * @param source 原始路径
     * @param variables 当前可见循环变量
     * @param templateCode 模板代码
     * @param tagPath XML 标签路径
     * @param line 起始行
     * @param column 起始列
     * @return 不可变受限路径
     */
    static CompiledDataPath compile(
            String source,
            Set<String> variables,
            String templateCode,
            String tagPath,
            int line,
            int column) {
        if (source == null || source.isBlank() || source.length() > XmlDsl.MAX_PATH_CHARACTERS) {
            throw invalid(templateCode, tagPath, line, column, "数据路径为空或超过长度限制");
        }
        String variableName = null;
        String fieldSource = source;
        if (source.startsWith("$")) {
            int separator = source.indexOf('.');
            variableName = separator < 0 ? source.substring(1) : source.substring(1, separator);
            fieldSource = separator < 0 ? "" : source.substring(separator + 1);
            if (separator >= 0 && fieldSource.isEmpty()) {
                throw invalid(templateCode, tagPath, line, column, "变量路径不能以空字段段结尾");
            }
            if (!VARIABLE.matcher(variableName).matches() || !variables.contains(variableName)) {
                throw invalid(templateCode, tagPath, line, column, "数据路径引用了未声明变量");
            }
        }
        List<String> segments = new ArrayList<>();
        if (!fieldSource.isEmpty()) {
            for (String segment : fieldSource.split("\\.", -1)) {
                if (!SEGMENT.matcher(segment).matches()) {
                    throw invalid(templateCode, tagPath, line, column, "数据路径包含禁止语法");
                }
                segments.add(segment);
            }
        }
        if (variableName == null && segments.isEmpty()) {
            throw invalid(templateCode, tagPath, line, column, "根数据路径必须包含字段段");
        }
        if (segments.size() > XmlDsl.MAX_PATH_SEGMENTS) {
            throw invalid(templateCode, tagPath, line, column, "数据路径字段段数量超过限制");
        }
        return new CompiledDataPath(variableName, segments, source);
    }

    /** @return 可选循环变量名 */
    String variableName() {
        return variableName;
    }

    /** @return 不可变字段段 */
    List<String> segments() {
        return segments;
    }

    /** @return 规范路径 */
    String displayPath() {
        return displayPath;
    }

    /** 创建包含安全模板位置的编译异常。 */
    private static PrintCompilationException invalid(
            String templateCode, String tagPath, int line, int column, String detail) {
        return PrintCompilationException.invalid(templateCode + "：" + tagPath
                + "，第 " + line + " 行，第 " + column + " 列：" + detail);
    }
}
