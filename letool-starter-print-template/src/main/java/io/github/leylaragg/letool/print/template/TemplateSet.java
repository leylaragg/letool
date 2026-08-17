package io.github.leylaragg.letool.print.template;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一次发布形成的不可变模板集合。
 *
 * @author leyland
 */
public final class TemplateSet {

    /** 发布方分配的集合版本。 */
    private final long version;

    /** 按模板代码排序的定义快照。 */
    private final Map<String, TemplateDefinition> definitions;

    /** 集合内容的稳定 SHA-256 摘要。 */
    private final String digest;

    /** 可直接打印的文档数量。 */
    private final int documentCount;

    /** 可复用片段数量。 */
    private final int fragmentCount;

    /** 仅允许包内工厂在完成校验后创建集合。 */
    TemplateSet(
            long version,
            Map<String, TemplateDefinition> definitions,
            String digest,
            int documentCount,
            int fragmentCount) {
        this.version = version;
        this.definitions = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(definitions, "definitions 不能为空")));
        this.digest = Objects.requireNonNull(digest, "digest 不能为空");
        this.documentCount = documentCount;
        this.fragmentCount = fragmentCount;
    }

    /** @return 模板集合版本 */
    public long version() {
        return version;
    }

    /** @return 集合的十六进制 SHA-256 摘要 */
    public String digest() {
        return digest;
    }

    /** @return 按模板代码排序的只读代码集合 */
    public Set<String> templateCodes() {
        return definitions.keySet();
    }

    /** @return 按模板代码排序的只读定义集合 */
    public Collection<TemplateDefinition> definitions() {
        return definitions.values();
    }

    /**
     * 查找模板定义。
     *
     * @param templateCode 模板代码
     * @return 对应定义，不存在时为空
     */
    public Optional<TemplateDefinition> find(String templateCode) {
        Objects.requireNonNull(templateCode, "templateCode 不能为空");
        return Optional.ofNullable(definitions.get(templateCode));
    }

    /**
     * 获取必须存在的模板定义。
     *
     * @param templateCode 模板代码
     * @return 对应定义
     * @throws PrintValidationException 模板不存在时抛出
     */
    public TemplateDefinition require(String templateCode) {
        Objects.requireNonNull(templateCode, "templateCode 不能为空");
        TemplateDefinition definition = definitions.get(templateCode);
        if (definition == null) {
            throw PrintValidationException.invalidRequest(
                    "模板集合中不存在模板：" + templateCode);
        }
        return definition;
    }

    /** @return 完整文档数量 */
    public int documentCount() {
        return documentCount;
    }

    /** @return 模板片段数量 */
    public int fragmentCount() {
        return fragmentCount;
    }
}
