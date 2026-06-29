package com.github.leyland.letool.oss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储（OSS）模块配置属性类，对应 YAML 中的 {@code letool.oss} 前缀。
 *
 * <p>该配置类聚合了阿里云 OSS、MinIO、腾讯云 COS 三大对象存储服务的连接参数。
 * 使用者可在 {@code application.yml} 中按如下结构配置：</p>
 *
 * <pre>{@code
 * letool:
 *   oss:
 *     enabled: true                   # 是否启用 OSS 模块，默认 true
 *     default-provider: minio         # 默认存储提供商：aliyun / minio / tencent-cos
 *     aliyun:
 *       endpoint: oss-cn-hangzhou.aliyuncs.com
 *       access-key-id: your-access-key-id
 *       access-key-secret: your-access-key-secret
 *       bucket: your-bucket
 *     minio:
 *       endpoint: http://localhost:9000
 *       access-key: minioadmin
 *       secret-key: minioadmin
 *       bucket: your-bucket
 *     tencent-cos:
 *       secret-id: your-secret-id
 *       secret-key: your-secret-key
 *       region: ap-guangzhou
 *       bucket: your-bucket
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.oss")
public class OssProperties {

    // ======================== 顶层属性 ========================

    /**
     * 是否启用 OSS 模块，默认 {@code true}。
     *
     * <p>设置为 {@code false} 时，将不会创建任何 OSS 相关 Bean。</p>
     */
    private boolean enabled = true;

    /**
     * 默认存储提供商标识，默认 {@code "minio"}。
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code aliyun} — 阿里云 OSS</li>
     *   <li>{@code minio} — MinIO 对象存储</li>
     *   <li>{@code tencent-cos} — 腾讯云 COS</li>
     * </ul>
     * 该值决定自动配置时创建哪个 {@link com.github.leyland.letool.oss.core.OssProvider} 实现。
     */
    private String defaultProvider = "minio";

    /**
     * 阿里云 OSS 配置。
     */
    private Aliyun aliyun = new Aliyun();

    /**
     * MinIO 配置。
     */
    private Minio minio = new Minio();

    /**
     * 腾讯云 COS 配置。
     */
    private TencentCos tencentCos = new TencentCos();

    // ======================== Getter / Setter ========================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Aliyun getAliyun() {
        return aliyun;
    }

    public void setAliyun(Aliyun aliyun) {
        this.aliyun = aliyun;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public TencentCos getTencentCos() {
        return tencentCos;
    }

    public void setTencentCos(TencentCos tencentCos) {
        this.tencentCos = tencentCos;
    }

    // ======================== 阿里云 OSS 配置 ========================

    /**
     * 阿里云 OSS 对象存储配置。
     *
     * <p>包含阿里云 OSS 服务连接所需的端点、认证密钥及默认 Bucket 名称。
     * 接入前需在阿里云控制台创建 RAM 用户并授予 OSS 操作权限。</p>
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Aliyun {

        /**
         * OSS 服务端点地址，如 {@code oss-cn-hangzhou.aliyuncs.com}。
         */
        private String endpoint;

        /**
         * RAM 用户 AccessKeyId。
         */
        private String accessKeyId;

        /**
         * RAM 用户 AccessKeySecret。
         */
        private String accessKeySecret;

        /**
         * 默认 Bucket 名称。
         */
        private String bucket;

        // ---- Getter / Setter ----

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    // ======================== MinIO 配置 ========================

    /**
     * MinIO 对象存储配置。
     *
     * <p>MinIO 是一款开源的高性能对象存储服务，兼容 Amazon S3 API。
     * 配置项包含服务端点、访问密钥及默认 Bucket 名称。</p>
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Minio {

        /**
         * MinIO 服务端点地址，如 {@code http://localhost:9000}。
         */
        private String endpoint;

        /**
         * 访问密钥（Access Key），对应 MinIO 的 {@code MINIO_ROOT_USER}。
         */
        private String accessKey;

        /**
         * 秘密密钥（Secret Key），对应 MinIO 的 {@code MINIO_ROOT_PASSWORD}。
         */
        private String secretKey;

        /**
         * 默认 Bucket 名称。
         */
        private String bucket;

        // ---- Getter / Setter ----

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    // ======================== 腾讯云 COS 配置 ========================

    /**
     * 腾讯云 COS（Cloud Object Storage）对象存储配置。
     *
     * <p>包含腾讯云 COS 服务连接所需的认证密钥、地域及默认 Bucket 名称。
     * 接入前需在腾讯云控制台获取 SecretId 和 SecretKey。</p>
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class TencentCos {

        /**
         * 腾讯云 API 密钥 SecretId。
         */
        private String secretId;

        /**
         * 腾讯云 API 密钥 SecretKey。
         */
        private String secretKey;

        /**
         * COS 所在地域，如 {@code ap-guangzhou}、{@code ap-shanghai}。
         */
        private String region;

        /**
         * 默认 Bucket 名称（需包含 APPID 后缀，格式为 {@code BucketName-APPID}）。
         */
        private String bucket;

        // ---- Getter / Setter ----

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
