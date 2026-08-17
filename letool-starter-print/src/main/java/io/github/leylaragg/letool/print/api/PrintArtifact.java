package io.github.leylaragg.letool.print.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 同步打印生成的不可变内存产物。
 *
 * <p>内容和元数据均与调用方隔离，SHA-256 由框架基于实际内容计算。</p>
 *
 * @author leyland
 */
public final class PrintArtifact {

    /** 产物格式。 */
    private final OutputFormat outputFormat;

    /** 产物字节的内部副本。 */
    private final byte[] content;

    /** 小写十六进制 SHA-256。 */
    private final String sha256;

    /** 不包含敏感业务数据的只读元数据。 */
    private final Map<String, String> metadata;

    /** 创建已校验的内存产物。 */
    private PrintArtifact(OutputFormat outputFormat, byte[] content, Map<String, String> metadata) {
        this.outputFormat = outputFormat;
        this.content = Arrays.copyOf(content, content.length);
        this.sha256 = sha256(content);
        this.metadata = copyMetadata(metadata);
    }

    /**
     * 创建内存打印产物。
     *
     * @param outputFormat 产物格式
     * @param content 非空产物字节
     * @param metadata 安全元数据；允许空集合但不允许空键值
     * @return 不可变打印产物
     * @throws IllegalArgumentException 内容为空或元数据包含空键值时抛出
     */
    public static PrintArtifact of(
            OutputFormat outputFormat,
            byte[] content,
            Map<String, String> metadata) {
        Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content 不能为空");
        }
        return new PrintArtifact(outputFormat, content, metadata);
    }

    /** @return 产物格式 */
    public OutputFormat outputFormat() {
        return outputFormat;
    }

    /** @return 产物内容的独立副本 */
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /**
     * 返回产物实际字节数，供统一输出限制校验使用。
     *
     * @return 产物字节数
     */
    public int contentLength() {
        return content.length;
    }

    /** @return 小写十六进制 SHA-256 */
    public String sha256() {
        return sha256;
    }

    /** @return 不可修改的安全元数据 */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** 复制并校验安全元数据。 */
    private static Map<String, String> copyMetadata(Map<String, String> metadata) {
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

    /** 计算内容摘要。 */
    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
