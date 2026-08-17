package io.github.leylaragg.letool.ruleengine.fact;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 由属性段和数组下标段组成的不可变规范事实路径。
 */
public final class FactPath {

    /** 按访问顺序冻结的属性段和下标段。 */
    private final List<Segment> segments;

    /** 与路径段一一对应的唯一规范文本。 */
    private final String canonicalPath;

    /**
     * 创建规范事实路径。
     *
     * @param segments 非空路径段
     */
    FactPath(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw RuleEngineException.invalidArgument();
        }
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.canonicalPath = render(this.segments);
    }

    /**
     * 事实解析器按顺序使用的不可变路径段。
     *
     * @return 路径段列表
     */
    public List<Segment> segments() {
        return segments;
    }

    /**
     * 判断当前路径是否为另一条路径的完整段前缀。
     *
     * @param other 待比较路径
     * @return 当前路径更短且所有对应段相同时返回 {@code true}
     */
    public boolean isStrictPrefixOf(FactPath other) {
        if (other == null || segments.size() >= other.segments.size()) {
            return false;
        }
        return segments.equals(other.segments.subList(0, segments.size()));
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FactPath that && segments.equals(that.segments);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    /** 返回唯一规范路径文本。 */
    @Override
    public String toString() {
        return canonicalPath;
    }

    /**
     * 把路径段转换为唯一规范文本。
     *
     * @param values 路径段
     * @return 规范路径
     */
    private static String render(List<Segment> values) {
        StringBuilder result = new StringBuilder();
        for (Segment segment : values) {
            if (segment instanceof PropertySegment property) {
                if (!result.isEmpty()) {
                    result.append('.');
                }
                result.append(property.name());
            } else if (segment instanceof IndexSegment index) {
                result.append('[').append(index.index()).append(']');
            }
        }
        return result.toString();
    }

    /**
     * 路径段标记接口。
     */
    public sealed interface Segment permits PropertySegment, IndexSegment {
    }

    /**
     * 对象属性路径段。
     */
    public static final class PropertySegment implements Segment {

        /** 已通过阶段一标识符规则校验的属性名。 */
        private final String name;

        /**
         * 创建属性路径段。
         *
         * @param name 合法属性名
         */
        public PropertySegment(String name) {
            if (!isValidPropertyName(name)) {
                throw RuleEngineException.invalidArgument();
            }
            this.name = name;
        }

        /**
         * 校验属性名是否符合阶段一固定语法。
         *
         * @param value 待校验属性名
         * @return 合法时返回 {@code true}
         */
        private static boolean isValidPropertyName(String value) {
            if (value == null || value.isEmpty() || !isAsciiLetterOrUnderscore(value.charAt(0))) {
                return false;
            }
            for (int index = 1; index < value.length(); index++) {
                char character = value.charAt(index);
                if (!isAsciiLetterOrUnderscore(character)
                        && !(character >= '0' && character <= '9')) {
                    return false;
                }
            }
            return true;
        }

        /** 属性首字符只接受 ASCII 字母或下划线。 */
        private static boolean isAsciiLetterOrUnderscore(char value) {
            return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value == '_';
        }

        /**
         * 规范属性名。
         *
         * @return 属性名
         */
        public String name() {
            return name;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof PropertySegment that && name.equals(that.name);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 数组下标路径段。
     */
    public static final class IndexSegment implements Segment {

        /** 已校验的非负数组下标。 */
        private final int index;

        /**
         * 创建数组下标段。
         *
         * @param index 非负数组下标
         */
        public IndexSegment(int index) {
            if (index < 0) {
                throw RuleEngineException.invalidArgument();
            }
            this.index = index;
        }

        /**
         * 规范的非负数组下标。
         *
         * @return 非负下标
         */
        public int index() {
            return index;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof IndexSegment that && index == that.index;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Integer.hashCode(index);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "[" + index + "]";
        }
    }
}
