package io.github.leylaragg.letool.ruleengine.internal.digest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 按长度前缀编码生成确定性 SHA-256 内容摘要。
 *
 * <p>规则引擎的契约、目录和编译产物都通过这个类型写入摘要字段，避免不同对象
 * 各自实现拼接规则后产生边界歧义。该类型位于 internal 包，不属于宿主扩展 API。</p>
 */
public final class DigestBuilder {

    /** 当前摘要实例，始终使用 JDK 必须提供的 SHA-256。 */
    private final MessageDigest digest;

    /**
     * 创建一个带领域标识的摘要构建器。
     *
     * @param domain 非空的摘要领域和格式版本
     */
    public DigestBuilder(String domain) {
        this.digest = sha256();
        add(domain);
    }

    /**
     * 写入一个带 UTF-8 长度前缀的字符串字段。
     *
     * @param value 非空字段值
     * @return 当前构建器
     */
    public DigestBuilder add(String value) {
        if (value == null) {
            throw new IllegalArgumentException("摘要字段不能为空");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        add(bytes.length);
        digest.update(bytes);
        return this;
    }

    /**
     * 写入固定四字节大端整数。
     *
     * @param value 整数字段
     * @return 当前构建器
     */
    public DigestBuilder add(int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
        return this;
    }

    /**
     * 完成本次摘要并返回小写十六进制文本。
     *
     * @return 六十四位 SHA-256 内容摘要
     */
    public String finish() {
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 获取 JDK 标准 SHA-256 实现；缺失时说明运行时本身不可用。 */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }
}
