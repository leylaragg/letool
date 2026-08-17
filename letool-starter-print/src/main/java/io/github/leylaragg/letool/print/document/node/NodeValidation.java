package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.regex.Pattern;

/**
 * 文档节点实现共享的包内校验规则。
 *
 * @author leyland
 */
final class NodeValidation {

    /** 允许的逻辑 ID 模式。 */
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** 禁止实例化校验工具。 */
    private NodeValidation() {
    }

    /** 校验可选逻辑 ID。 */
    static String optionalId(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            throw PrintValidationException.invalidDocument("节点 ID 不合法：" + id);
        }
        return id;
    }

    /** 校验必填逻辑 ID。 */
    static String requiredId(String id) {
        String normalized = optionalId(id);
        if (normalized.isEmpty()) {
            throw PrintValidationException.invalidDocument("节点 ID 不能为空");
        }
        return normalized;
    }
}
