package io.github.leylaragg.letool.print.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可扩展的打印模板格式标识。
 *
 * <p>标识在构造时转为小写并校验，值对象不可变且线程安全。</p>
 *
 * @author leyland
 */
public final class TemplateFormat {

    /** 规范化后的模板格式标识。 */
    private final String value;

    /** 允许的格式标识模式。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /** Letool 受控 XML 模板格式。 */
    public static final TemplateFormat LETOOL_XML = new TemplateFormat("letool-xml");

    /**
     * 创建并规范化模板格式。
     *
     * @param value 模板格式标识，忽略首尾空白和大小写
     * @throws IllegalArgumentException 标识为空白或包含不安全字符时抛出
     */
    public TemplateFormat(String value) {
        this.value = normalize(value);
    }

    /**
     * 返回规范化后的模板格式标识。
     *
     * @return 模板格式标识
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof TemplateFormat that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "TemplateFormat[value=" + value + "]";
    }

    /** 规范化并校验模板格式标识。 */
    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("templateFormat 不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("templateFormat 格式不合法：" + normalized);
        }
        return normalized;
    }
}
