package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 保存完成引用解析和安全校验后的 XML 模板集合。
 *
 * @author leyland
 */
public final class CompiledXmlTemplateSet {

    /** 模板集合版本。 */
    private final long templateSetVersion;

    /** 来源模板集合的稳定摘要。 */
    private final String templateSetDigest;

    /** 按模板代码排序的可打印文档。 */
    private final Map<String, CompiledXmlTemplate> documents;

    /**
     * 固化本次集合编译产生的文档。
     *
     * @param templateSetVersion 模板集合版本
     * @param templateSetDigest 模板集合摘要
     * @param documents 已编译的可打印文档
     */
    CompiledXmlTemplateSet(long templateSetVersion, String templateSetDigest,
                           Map<String, CompiledXmlTemplate> documents) {
        this.templateSetVersion = templateSetVersion;
        this.templateSetDigest = Objects.requireNonNull(templateSetDigest, "templateSetDigest 不能为空");
        TreeMap<String, CompiledXmlTemplate> sorted = new TreeMap<>(
                Objects.requireNonNull(documents, "documents 不能为空"));
        this.documents = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    /** @return 模板集合版本 */
    public long templateSetVersion() {
        return templateSetVersion;
    }

    /** @return 来源模板集合摘要 */
    public String templateSetDigest() {
        return templateSetDigest;
    }

    /** @return 按模板代码排序的只读编码集合 */
    public Set<String> documentCodes() {
        return documents.keySet();
    }

    /** @return 按模板代码排序的只读文档集合 */
    public Collection<CompiledXmlTemplate> documents() {
        return documents.values();
    }

    /**
     * 查找可打印文档。
     *
     * @param templateCode 模板代码
     * @return 对应文档，不存在时为空
     */
    public Optional<CompiledXmlTemplate> find(String templateCode) {
        Objects.requireNonNull(templateCode, "templateCode 不能为空");
        return Optional.ofNullable(documents.get(templateCode));
    }

    /**
     * 获取指定的可打印文档，不存在时直接报告请求错误。
     *
     * @param templateCode 模板代码
     * @return 对应编译文档
     * @throws PrintValidationException 模板集合中没有该文档时抛出
     */
    public CompiledXmlTemplate require(String templateCode) {
        Objects.requireNonNull(templateCode, "templateCode 不能为空");
        CompiledXmlTemplate template = documents.get(templateCode);
        if (template == null) {
            throw PrintValidationException.invalidRequest("XML 模板集合中不存在文档：" + templateCode);
        }
        return template;
    }
}
