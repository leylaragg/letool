package io.github.leylaragg.letool.oss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSS 公共配置。
 *
 * <p>厂商连接参数由对应 Provider starter 独立管理，公共模块只保存启用开关、当前
 * Provider 标识和快捷操作使用的默认 Bucket。</p>
 */
@ConfigurationProperties(prefix = "letool.oss")
public class OssProperties {

    /** 是否启用 OSS 模块，默认关闭。 */
    private boolean enabled;

    /** 当前对象存储 Provider，默认使用 MinIO。 */
    private String provider = "minio";

    /** 快捷操作使用的默认 Bucket。 */
    private String bucket;

    /**
     * 判断是否启用 OSS 模块。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 OSS 模块。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取当前 Provider 标识。
     *
     * @return Provider 标识
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置当前 Provider 标识。
     *
     * @param provider Provider 标识
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取默认 Bucket。
     *
     * @return Bucket 名称
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 设置默认 Bucket。
     *
     * @param bucket Bucket 名称
     */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
