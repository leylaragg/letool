package io.github.leylaragg.letool.print.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 统一校验并复制打印元数据的工具类。 */
final class PrintMetadata {

    private PrintMetadata() {
    }

    /**
     * 复制并校验元数据，隔离调用方后返回不可修改副本。
     *
     * @param metadata 待复制的元数据
     * @return 安全元数据副本
     * @throws NullPointerException 元数据引用为空时抛出
     * @throws IllegalArgumentException 元数据包含空键或空值时抛出
     */
    static Map<String, String> copyOf(Map<String, String> metadata) {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Map<String, String> copy = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("metadata 不允许空键或空值");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
