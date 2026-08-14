package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.exception.PrintValidationException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 负责校验、排序并构造模板集合快照。
 *
 * @author leyland
 */
final class TemplateSetFactory {

    /** 单个集合最多包含的模板数量。 */
    private static final int MAX_TEMPLATES = 10_000;

    /** 单个集合允许的模板正文总字节数。 */
    private static final long MAX_TOTAL_CONTENT_BYTES = 256L * 1024 * 1024;

    /** 当前工厂允许的模板数量。 */
    private final int maxTemplates;

    /** 当前工厂允许的正文总字节数。 */
    private final long maxTotalContentBytes;

    /** 创建生产环境使用的固定限制工厂。 */
    static TemplateSetFactory standard() {
        return new TemplateSetFactory(MAX_TEMPLATES, MAX_TOTAL_CONTENT_BYTES);
    }

    /**
     * 创建可调整上限的包内工厂，便于边界测试。
     *
     * @param maxTemplates 模板数量上限
     * @param maxTotalContentBytes 正文总字节数上限
     */
    TemplateSetFactory(int maxTemplates, long maxTotalContentBytes) {
        if (maxTemplates <= 0 || maxTotalContentBytes <= 0) {
            throw new IllegalArgumentException("模板数量和正文总字节数上限必须为正数");
        }
        this.maxTemplates = maxTemplates;
        this.maxTotalContentBytes = maxTotalContentBytes;
    }

    /**
     * 将一批模板定义整理为不可变集合。
     *
     * @param version 集合版本
     * @param source 模板定义
     * @return 完整模板集合
     */
    TemplateSet create(long version, Collection<TemplateDefinition> source) {
        requirePositiveVersion(version);
        Objects.requireNonNull(source, "definitions 不能为空");
        if (source.isEmpty() || source.size() > maxTemplates) {
            throw invalid("模板集合数量不合法");
        }

        Map<String, TemplateDefinition> sorted = new TreeMap<>();
        long totalContentBytes = 0;
        int documentCount = 0;
        int fragmentCount = 0;
        for (TemplateDefinition definition : source) {
            Objects.requireNonNull(definition, "模板定义不能为空");
            PrintTemplate template = definition.template();
            if (template.templateSetVersion() != version) {
                throw invalid("模板集合版本不一致");
            }
            if (sorted.putIfAbsent(template.templateCode(), definition) != null) {
                throw invalid("模板代码重复：" + template.templateCode());
            }

            totalContentBytes += template.content().length;
            if (totalContentBytes > maxTotalContentBytes) {
                throw invalid("模板集合内容超过安全限制");
            }
            if (definition.type() == TemplateType.DOCUMENT) {
                documentCount++;
            } else {
                fragmentCount++;
            }
        }
        if (documentCount == 0) {
            throw invalid("模板集合至少需要一个 DOCUMENT");
        }

        String digest = digest(version, sorted.values());
        return new TemplateSet(version, sorted, digest, documentCount, fragmentCount);
    }

    /** 将集合的稳定字段依次写入 SHA-256。 */
    private String digest(long version, Collection<TemplateDefinition> definitions) {
        MessageDigest digest = sha256();
        updateLong(digest, version);
        updateInt(digest, definitions.size());
        for (TemplateDefinition definition : definitions) {
            PrintTemplate template = definition.template();
            updateText(digest, definition.type().name());
            updateText(digest, template.templateCode());
            updateText(digest, template.templateFormat().value());
            updateInt(digest, template.dslVersion());
            updateLong(digest, template.templateSetVersion());
            updateInt(digest, template.contextVersion());
            byte[] content = template.content();
            updateInt(digest, content.length);
            digest.update(content);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 获取所有 JDK 都必须支持的 SHA-256 实现。 */
    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /** 写入带长度的 UTF-8 文本，避免字段边界混淆。 */
    private void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    /** 按固定宽度写入整数。 */
    private void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /** 按固定宽度写入长整数。 */
    private void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    /** 集合版本由发布方提供，必须是正整数。 */
    private void requirePositiveVersion(long version) {
        if (version <= 0) {
            throw invalid("模板集合版本必须为正整数");
        }
    }

    /** 创建不携带模板正文的请求校验异常。 */
    private PrintValidationException invalid(String detail) {
        return PrintValidationException.invalidRequest(detail);
    }
}
