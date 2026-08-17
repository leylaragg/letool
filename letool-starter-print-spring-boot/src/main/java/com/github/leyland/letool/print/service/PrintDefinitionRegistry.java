package com.github.leyland.letool.print.service;

import com.github.leyland.letool.print.exception.PrintValidationException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按业务定义编码保存不可变打印定义快照。
 *
 * @author leyland
 */
public final class PrintDefinitionRegistry {

    /** 保持 Spring 收集顺序的只读定义索引。 */
    private final Map<String, PrintDefinition<?>> definitions;

    /**
     * 冻结宿主声明的全部业务打印定义。
     *
     * @param definitions 可以为空的业务定义集合
     * @throws IllegalArgumentException 定义项为空或编码重复时抛出
     * @throws NullPointerException 集合为空时抛出
     */
    public PrintDefinitionRegistry(Collection<? extends PrintDefinition<?>> definitions) {
        Objects.requireNonNull(definitions, "definitions 不能为空");
        Map<String, PrintDefinition<?>> snapshot = new LinkedHashMap<>();
        for (PrintDefinition<?> definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("业务打印定义不能为 null");
            }
            if (snapshot.putIfAbsent(definition.code(), definition) != null) {
                throw new IllegalArgumentException("业务打印定义编码重复：" + definition.code());
            }
        }
        this.definitions = Collections.unmodifiableMap(snapshot);
    }

    /**
     * 获取业务调用指定的打印定义。
     *
     * @param code 稳定业务定义编码
     * @return 已注册定义
     * @throws PrintValidationException 编码为空或不存在时抛出
     */
    public PrintDefinition<?> require(String code) {
        if (code == null || code.isBlank()) {
            throw PrintValidationException.invalidRequest("业务打印定义编码不能为空");
        }
        PrintDefinition<?> definition = definitions.get(code);
        if (definition == null) {
            throw PrintValidationException.invalidRequest("业务打印定义不存在");
        }
        return definition;
    }

    /** @return 与注册顺序一致的只读定义编码 */
    public Set<String> registeredCodes() {
        return definitions.keySet();
    }
}
