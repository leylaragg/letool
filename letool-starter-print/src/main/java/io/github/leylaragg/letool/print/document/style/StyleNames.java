package io.github.leylaragg.letool.print.document.style;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.regex.Pattern;

/**
 * 命名样式共享的安全标识规则。
 *
 * @author leyland
 */
final class StyleNames {

    /** 小写样式名称模式。 */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /** 禁止实例化名称校验工具。 */
    private StyleNames() {
    }

    /** 校验必填样式名称。 */
    static String required(String name) {
        String normalized = optional(name);
        if (normalized.isEmpty()) {
            throw PrintValidationException.invalidDocument("样式名称不能为空");
        }
        return normalized;
    }

    /** 校验可选样式引用。 */
    static String optional(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (!NAME.matcher(name).matches()) {
            throw PrintValidationException.invalidDocument("样式名称不合法：" + safe(name));
        }
        return name;
    }

    /** 异常只保留短小名称，避免把不受控长文本带入消息。 */
    private static String safe(String name) {
        return name.length() <= 64 ? name : name.substring(0, 64);
    }
}
