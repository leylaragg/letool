package io.github.leylaragg.letool.oss.model;

/**
 * 描述对象上传成功后的稳定身份信息。
 *
 * <p>上传成功不代表对象可以匿名访问，因此结果不虚构公开 URL。需要临时访问地址时，
 * 应单独调用预签名 URL 接口。</p>
 */
public final class OssUploadResult {

    private final String provider;
    private final String bucket;
    private final String objectKey;
    private final String etag;
    private final String versionId;

    /**
     * 创建上传结果。
     *
     * @param provider Provider 标识
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param etag 服务端返回的 ETag；未返回时可为 {@code null}
     * @param versionId 对象版本号；未启用版本控制时可为 {@code null}
     */
    public OssUploadResult(String provider, String bucket, String objectKey, String etag, String versionId) {
        this.provider = requireText(provider, "provider");
        this.bucket = requireText(bucket, "bucket");
        this.objectKey = requireText(objectKey, "objectKey");
        this.etag = etag;
        this.versionId = versionId;
    }

    /**
     * 获取 Provider 标识。
     *
     * @return Provider 标识
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 获取 Bucket 名称。
     *
     * @return Bucket 名称
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 获取对象键。
     *
     * @return 对象键
     */
    public String getObjectKey() {
        return objectKey;
    }

    /**
     * 获取对象 ETag。
     *
     * @return ETag；服务端未返回时为 {@code null}
     */
    public String getEtag() {
        return etag;
    }

    /**
     * 获取对象版本号。
     *
     * @return 版本号；未启用版本控制时为 {@code null}
     */
    public String getVersionId() {
        return versionId;
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param fieldName 字段名称
     * @return 已校验文本
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
