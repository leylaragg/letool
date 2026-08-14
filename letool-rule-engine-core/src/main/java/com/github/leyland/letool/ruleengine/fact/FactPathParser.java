package com.github.leyland.letool.ruleengine.fact;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段一事实路径解析器，仅接受属性和非负整数下标。
 */
public final class FactPathParser {

    /** 工具类不允许实例化。 */
    private FactPathParser() {
    }

    /**
     * 解析普通路径或完整的 {@code ${path}} 包装。
     *
     * @param source 路径文本
     * @return 规范事实路径
     * @throws RuleEngineException 路径不符合阶段一语法时抛出
     */
    public static FactPath parse(String source) {
        String path = unwrap(source);
        List<FactPath.Segment> segments = new ArrayList<>();
        int cursor = 0;
        boolean propertyExpected = true;
        while (cursor < path.length()) {
            if (propertyExpected) {
                int start = cursor;
                if (!isPropertyStart(path.charAt(cursor))) {
                    throw RuleEngineException.invalidArgument();
                }
                cursor++;
                while (cursor < path.length() && isPropertyPart(path.charAt(cursor))) {
                    cursor++;
                }
                String property = path.substring(start, cursor);
                if (property.equals("class")) throw RuleEngineException.invalidArgument();
                segments.add(new FactPath.PropertySegment(property));
                propertyExpected = false;
                continue;
            }
            char current = path.charAt(cursor);
            if (current == '.') {
                cursor++;
                propertyExpected = true;
            } else if (current == '[') {
                cursor = parseIndex(path, cursor, segments);
            } else {
                throw RuleEngineException.invalidArgument();
            }
        }
        if (propertyExpected) {
            throw RuleEngineException.invalidArgument();
        }
        return new FactPath(segments);
    }

    /**
     * 去除完整插值包装，拒绝残缺或嵌套包装。
     *
     * @param source 原始文本
     * @return 待解析路径
     */
    private static String unwrap(String source) {
        if (source == null || source.isBlank() || !source.equals(source.trim())) {
            throw RuleEngineException.invalidArgument();
        }
        if (source.startsWith("${")) {
            if (!source.endsWith("}") || source.length() <= 3) {
                throw RuleEngineException.invalidArgument();
            }
            String value = source.substring(2, source.length() - 1);
            if (value.indexOf('$') >= 0 || value.indexOf('{') >= 0 || value.indexOf('}') >= 0) {
                throw RuleEngineException.invalidArgument();
            }
            return value;
        }
        if (source.indexOf('$') >= 0 || source.indexOf('{') >= 0 || source.indexOf('}') >= 0) {
            throw RuleEngineException.invalidArgument();
        }
        return source;
    }

    /**
     * 解析数组下标并追加路径段。
     *
     * @param path 路径文本
     * @param opening 左方括号位置
     * @param segments 路径段结果
     * @return 右方括号后的游标
     */
    private static int parseIndex(
            String path,
            int opening,
            List<FactPath.Segment> segments) {
        int cursor = opening + 1;
        int start = cursor;
        while (cursor < path.length() && Character.isDigit(path.charAt(cursor))) {
            cursor++;
        }
        if (start == cursor || cursor >= path.length() || path.charAt(cursor) != ']') {
            throw RuleEngineException.invalidArgument();
        }
        try {
            int index = Integer.parseInt(path.substring(start, cursor));
            segments.add(new FactPath.IndexSegment(index));
        } catch (NumberFormatException exception) {
            throw RuleEngineException.invalidArgument();
        }
        return cursor + 1;
    }

    /** 属性首字符只接受 ASCII 字母或下划线。 */
    private static boolean isPropertyStart(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value == '_';
    }

    /** 属性后续字符可额外接受 ASCII 数字。 */
    private static boolean isPropertyPart(char value) {
        return isPropertyStart(value) || value >= '0' && value <= '9';
    }
}
