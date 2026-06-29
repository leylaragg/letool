package com.github.leyland.letool.oss.core;

import com.github.leyland.letool.oss.config.OssProperties;
import com.github.leyland.letool.oss.exception.OssException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;

/**
 * OSS 对象存储操作模板，是业务代码操作对象存储的统一入口。
 *
 * <p>该类封装了 {@link OssProvider} 和 {@link OssProperties}，提供以下便利：</p>
 * <ul>
 *   <li><b>默认 Bucket：</b>快速方法自动使用配置中的默认 Bucket，无需每次手动指定。</li>
 *   <li><b>Builder 模式：</b>通过链式调用灵活设置 Bucket、ObjectKey、ContentType，
 *       提高代码可读性。</li>
 *   <li><b>统一异常处理：</b>所有异常统一转换为 {@link OssException}。</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 快速上传（使用默认 Bucket）
 * ossTemplate.upload("photos/avatar.png", inputStream);
 *
 * // 使用 Builder 模式
 * ossTemplate.builder()
 *         .bucket("my-bucket")
 *         .objectKey("photos/avatar.png")
 *         .contentType("image/png")
 *         .upload(inputStream);
 *
 * // 下载文件
 * InputStream is = ossTemplate.download("photos/avatar.png");
 *
 * // 获取预签名 URL（有效期 1 小时）
 * String url = ossTemplate.getPresignedUrl("photos/avatar.png", Duration.ofHours(1));
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class OssTemplate {

    private static final Logger log = LoggerFactory.getLogger(OssTemplate.class);

    // ======================== 成员变量 ========================

    /**
     * 底层 OSS 存储提供者，执行实际的对象存储操作。
     */
    private final OssProvider ossProvider;

    /**
     * OSS 配置属性，用于获取默认 Bucket 等信息。
     */
    private final OssProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 构造 OSS 操作模板。
     *
     * @param ossProvider 底层 OSS 存储提供者
     * @param properties  OSS 配置属性
     */
    public OssTemplate(OssProvider ossProvider, OssProperties properties) {
        this.ossProvider = ossProvider;
        this.properties = properties;
    }

    // ======================== 快捷操作（使用默认 Bucket） ========================

    /**
     * 使用默认 Bucket 上传文件。
     *
     * <p>默认 Bucket 由 {@code letool.oss.{default-provider}.bucket} 配置决定。
     * Content-Type 默认使用 {@code application/octet-stream}。</p>
     *
     * @param objectKey   对象键
     * @param inputStream 文件内容输入流
     * @return 对象在存储系统中的 URL 或标识
     * @throws OssException 上传失败时抛出
     */
    public String upload(String objectKey, InputStream inputStream) {
        return upload(getDefaultBucket(), objectKey, inputStream, "application/octet-stream");
    }

    /**
     * 使用默认 Bucket 和指定 Content-Type 上传文件。
     *
     * @param objectKey   对象键
     * @param inputStream 文件内容输入流
     * @param contentType MIME 类型
     * @return 对象在存储系统中的 URL 或标识
     * @throws OssException 上传失败时抛出
     */
    public String upload(String objectKey, InputStream inputStream, String contentType) {
        return upload(getDefaultBucket(), objectKey, inputStream, contentType);
    }

    /**
     * 使用默认 Bucket 下载文件。
     *
     * @param objectKey 对象键
     * @return 文件内容的输入流
     * @throws OssException 下载失败时抛出
     */
    public InputStream download(String objectKey) {
        return ossProvider.download(getDefaultBucket(), objectKey);
    }

    /**
     * 使用默认 Bucket 删除文件。
     *
     * @param objectKey 对象键
     * @return {@code true} 删除成功，{@code false} 文件不存在
     * @throws OssException 删除失败时抛出
     */
    public boolean delete(String objectKey) {
        return ossProvider.delete(getDefaultBucket(), objectKey);
    }

    /**
     * 使用默认 Bucket 获取预签名 URL。
     *
     * @param objectKey  对象键
     * @param expiration 预签名 URL 有效期
     * @return 预签名 URL 字符串
     * @throws OssException 生成失败时抛出
     */
    public String getPresignedUrl(String objectKey, Duration expiration) {
        return ossProvider.getPresignedUrl(getDefaultBucket(), objectKey, expiration);
    }

    /**
     * 使用默认 Bucket 检查对象是否存在。
     *
     * @param objectKey 对象键
     * @return {@code true} 存在，{@code false} 不存在
     * @throws OssException 检查失败时抛出
     */
    public boolean exists(String objectKey) {
        return ossProvider.exists(getDefaultBucket(), objectKey);
    }

    // ======================== 完整操作（指定 Bucket） ========================

    /**
     * 上传文件到指定 Bucket。
     *
     * <p>需要指定完整参数：Bucket 名称、对象键、输入流和 Content-Type。
     * 通常由 Builder 模式或需要精确控制时使用。</p>
     *
     * @param bucket      目标 Bucket 名称
     * @param objectKey   对象键
     * @param inputStream 文件内容输入流
     * @param contentType MIME 类型
     * @return 对象在存储系统中的 URL 或标识
     * @throws OssException 上传失败时抛出
     */
    public String upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
        log.debug("Uploading object: bucket={}, key={}, contentType={}", bucket, objectKey, contentType);
        try {
            return ossProvider.upload(bucket, objectKey, inputStream, contentType);
        } catch (OssException e) {
            throw e;
        } catch (Exception e) {
            throw new OssException("Failed to upload object: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    /**
     * 从指定 Bucket 下载文件。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键
     * @return 文件内容的输入流
     * @throws OssException 下载失败时抛出
     */
    public InputStream download(String bucket, String objectKey) {
        log.debug("Downloading object: bucket={}, key={}", bucket, objectKey);
        try {
            return ossProvider.download(bucket, objectKey);
        } catch (OssException e) {
            throw e;
        } catch (Exception e) {
            throw new OssException("Failed to download object: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    /**
     * 从指定 Bucket 删除文件。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键
     * @return {@code true} 删除成功，{@code false} 文件不存在
     * @throws OssException 删除失败时抛出
     */
    public boolean delete(String bucket, String objectKey) {
        log.debug("Deleting object: bucket={}, key={}", bucket, objectKey);
        try {
            return ossProvider.delete(bucket, objectKey);
        } catch (OssException e) {
            throw e;
        } catch (Exception e) {
            throw new OssException("Failed to delete object: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    /**
     * 获取指定 Bucket 中对象的预签名 URL。
     *
     * @param bucket     目标 Bucket 名称
     * @param objectKey  对象键
     * @param expiration 预签名 URL 有效期
     * @return 预签名 URL 字符串
     * @throws OssException 生成失败时抛出
     */
    public String getPresignedUrl(String bucket, String objectKey, Duration expiration) {
        log.debug("Generating presigned URL: bucket={}, key={}, expiration={}", bucket, objectKey, expiration);
        try {
            return ossProvider.getPresignedUrl(bucket, objectKey, expiration);
        } catch (OssException e) {
            throw e;
        } catch (Exception e) {
            throw new OssException("Failed to generate presigned URL: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    /**
     * 检查指定 Bucket 中的对象是否存在。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键
     * @return {@code true} 存在，{@code false} 不存在
     * @throws OssException 检查失败时抛出
     */
    public boolean exists(String bucket, String objectKey) {
        try {
            return ossProvider.exists(bucket, objectKey);
        } catch (OssException e) {
            throw e;
        } catch (Exception e) {
            throw new OssException("Failed to check object existence: bucket=" + bucket + ", key=" + objectKey, e);
        }
    }

    // ======================== Builder 模式 ========================

    /**
     * 创建一个新的 Builder 实例，用于链式构建上传/下载/删除操作。
     *
     * <p>Builder 模式允许在调用前灵活设置参数，未设置的字段将在执行时使用默认值：</p>
     * <ul>
     *   <li>{@code bucket} — 默认使用配置中的 Bucket</li>
     *   <li>{@code contentType} — 默认 {@code application/octet-stream}</li>
     * </ul>
     *
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 上传到自定义 Bucket
     * ossTemplate.builder()
     *         .bucket("archive-bucket")
     *         .objectKey("2024/report.pdf")
     *         .contentType("application/pdf")
     *         .upload(fileStream);
     *
     * // 从默认 Bucket 下载
     * InputStream is = ossTemplate.builder()
     *         .objectKey("photos/avatar.png")
     *         .download();
     * }</pre>
     *
     * @return 新的 Builder 实例
     */
    public Builder builder() {
        return new Builder(this, properties);
    }

    // ======================== 内部工具方法 ========================

    /**
     * 获取当前配置的默认 Bucket 名称。
     *
     * <p>根据 {@code letool.oss.default-provider} 配置，从对应提供商的配置中提取 Bucket 名称。</p>
     *
     * @return 默认 Bucket 名称
     * @throws OssException 如果无法确定默认 Bucket 时抛出
     */
    private String getDefaultBucket() {
        String provider = properties.getDefaultProvider();
        String bucket;
        switch (provider.toLowerCase()) {
            case "aliyun":
                bucket = properties.getAliyun().getBucket();
                break;
            case "tencent-cos":
                bucket = properties.getTencentCos().getBucket();
                break;
            case "minio":
            default:
                bucket = properties.getMinio().getBucket();
                break;
        }
        if (bucket == null || bucket.isEmpty()) {
            throw new OssException("Default bucket is not configured for provider: " + provider
                    + ". Please set letool.oss." + provider.toLowerCase() + ".bucket in configuration.");
        }
        return bucket;
    }

    // ======================== Builder 内部类 ========================

    /**
     * OSS 操作 Builder，支持链式调用设置参数并执行操作。
     *
     * <p>Builder 是不可变延迟执行的 —— 参数设置后只有在调用终端方法（upload/download/delete 等）
     * 时才会触发实际的 OSS 操作。每个 Builder 实例只能执行一次操作。</p>
     *
     * @author leyland
     * @since 2.0.0
     */
    public static class Builder {

        /**
         * 持有外部 OssTemplate 实例，用于执行终端操作。
         */
        private final OssTemplate template;

        /**
         * OSS 配置属性，用于获取默认值。
         */
        private final OssProperties properties;

        /**
         * 目标 Bucket 名称，null 表示使用默认值。
         */
        private String bucket;

        /**
         * 对象键（必填）。
         */
        private String objectKey;

        /**
         * 文件 MIME 类型，null 表示使用默认值 {@code application/octet-stream}。
         */
        private String contentType;

        /**
         * 构造 Builder 实例。
         *
         * @param template   外部 OssTemplate 实例
         * @param properties OSS 配置属性
         */
        Builder(OssTemplate template, OssProperties properties) {
            this.template = template;
            this.properties = properties;
        }

        // ======================== 参数设置方法 ========================

        /**
         * 设置目标 Bucket 名称。
         *
         * <p>若不设置，将使用配置中的默认 Bucket。</p>
         *
         * @param bucket Bucket 名称
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /**
         * 设置对象键（即文件在 Bucket 中的路径标识，必填）。
         *
         * @param objectKey 对象键
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder objectKey(String objectKey) {
            this.objectKey = objectKey;
            return this;
        }

        /**
         * 设置文件的 MIME 类型。
         *
         * <p>若不设置，默认使用 {@code application/octet-stream}。</p>
         *
         * @param contentType MIME 类型（如 {@code image/png}、{@code application/pdf}）
         * @return 当前 Builder 实例（链式调用）
         */
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        // ======================== 终端操作方法 ========================

        /**
         * 执行文件上传操作。
         *
         * @param inputStream 文件内容输入流
         * @return 对象在存储系统中的 URL 或标识
         * @throws OssException 上传失败时抛出
         * @throws IllegalArgumentException objectKey 为空时抛出
         */
        public String upload(InputStream inputStream) {
            validateObjectKey();
            String targetBucket = resolveBucket();
            String targetContentType = resolveContentType();
            return template.upload(targetBucket, objectKey, inputStream, targetContentType);
        }

        /**
         * 执行文件下载操作。
         *
         * @return 文件内容的输入流
         * @throws OssException 下载失败时抛出
         * @throws IllegalArgumentException objectKey 为空时抛出
         */
        public InputStream download() {
            validateObjectKey();
            String targetBucket = resolveBucket();
            return template.download(targetBucket, objectKey);
        }

        /**
         * 执行文件删除操作。
         *
         * @return {@code true} 删除成功，{@code false} 文件不存在
         * @throws OssException 删除失败时抛出
         * @throws IllegalArgumentException objectKey 为空时抛出
         */
        public boolean delete() {
            validateObjectKey();
            String targetBucket = resolveBucket();
            return template.delete(targetBucket, objectKey);
        }

        /**
         * 获取预签名 URL。
         *
         * @param expiration 预签名 URL 有效期
         * @return 预签名 URL 字符串
         * @throws OssException 生成失败时抛出
         * @throws IllegalArgumentException objectKey 为空时抛出
         */
        public String getPresignedUrl(Duration expiration) {
            validateObjectKey();
            String targetBucket = resolveBucket();
            return template.getPresignedUrl(targetBucket, objectKey, expiration);
        }

        /**
         * 检查对象是否存在。
         *
         * @return {@code true} 存在，{@code false} 不存在
         * @throws OssException 检查失败时抛出
         * @throws IllegalArgumentException objectKey 为空时抛出
         */
        public boolean exists() {
            validateObjectKey();
            String targetBucket = resolveBucket();
            return template.exists(targetBucket, objectKey);
        }

        // ======================== 内部辅助方法 ========================

        /**
         * 校验 objectKey 不为空。
         *
         * @throws IllegalArgumentException objectKey 为 {@code null} 或空白时抛出
         */
        private void validateObjectKey() {
            if (objectKey == null || objectKey.isBlank()) {
                throw new IllegalArgumentException("objectKey must not be null or blank");
            }
        }

        /**
         * 解析最终使用的 Bucket 名称。
         *
         * <p>优先使用 Builder 中设置的 Bucket，未设置时使用配置的默认 Bucket。</p>
         *
         * @return Bucket 名称
         */
        private String resolveBucket() {
            if (bucket != null && !bucket.isBlank()) {
                return bucket;
            }
            return template.getDefaultBucket();
        }

        /**
         * 解析最终使用的 Content-Type。
         *
         * <p>优先使用 Builder 中设置的 Content-Type，未设置时使用默认值。</p>
         *
         * @return MIME 类型字符串
         */
        private String resolveContentType() {
            if (contentType != null && !contentType.isBlank()) {
                return contentType;
            }
            return "application/octet-stream";
        }
    }
}
