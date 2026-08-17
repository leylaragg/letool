package io.github.leylaragg.letool.print.xml.format;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按稳定名称保存格式化器的不可变注册表。
 *
 * @author leyland
 */
public final class PrintFormatterRegistry {

    /** 格式化器名称白名单。 */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    /** 保持注册顺序的不可变格式化器索引。 */
    private final Map<String, PrintValueFormatter> formatters;

    /**
     * 创建格式化器注册表快照。
     *
     * @param formatters 待注册格式化器
     */
    public PrintFormatterRegistry(Collection<? extends PrintValueFormatter> formatters) {
        Objects.requireNonNull(formatters, "formatters 不能为空");
        Map<String, PrintValueFormatter> snapshot = new LinkedHashMap<>();
        for (PrintValueFormatter formatter : formatters) {
            if (formatter == null) {
                throw new IllegalArgumentException("格式化器不能为 null");
            }
            String name = formatter.name();
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("格式化器名称不合法");
            }
            if (snapshot.putIfAbsent(name, formatter) != null) {
                throw new IllegalArgumentException("格式化器名称重复：" + name);
            }
        }
        this.formatters = Collections.unmodifiableMap(snapshot);
    }

    /**
     * 查找必需格式化器。
     *
     * @param name 格式化器名称
     * @return 已注册格式化器
     */
    public PrintValueFormatter require(String name) {
        PrintValueFormatter formatter = formatters.get(name);
        if (formatter == null) {
            throw new IllegalArgumentException("格式化器不存在：" + name);
        }
        return formatter;
    }

    /**
     * 返回不可变名称视图。
     *
     * @return 保持注册顺序的名称集合
     */
    public Set<String> names() {
        return formatters.keySet();
    }
}
