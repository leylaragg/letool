package com.github.leyland.letool.oss.model;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述从对象存储下载的对象及其元数据。
 *
 * <p>该对象持有远程响应流。调用方必须使用 try-with-resources 或显式调用
 * {@link #close()}，以便及时归还底层 HTTP 连接。</p>
 */
public final class OssObject implements AutoCloseable {

    private final String bucket;
    private final String objectKey;
    private final InputStream content;
    private final long contentLength;
    private final String contentType;
    private final String etag;
    private final Instant lastModified;
    private final Map<String, String> metadata;

    /**
     * 使用构建器创建下载对象。
     *
     * @param builder 已完成参数设置的构建器
     */
    private OssObject(Builder builder) {
        this.bucket = requireText(builder.bucket, "bucket");
        this.objectKey = requireText(builder.objectKey, "objectKey");
        this.content = requireValue(builder.content, "content");
        if (builder.contentLength < OssUploadRequest.UNKNOWN_CONTENT_LENGTH) {
            throw new IllegalArgumentException("contentLength must be -1 or greater");
        }
        this.contentLength = builder.contentLength;
        this.contentType = builder.contentType;
        this.etag = builder.etag;
        this.lastModified = builder.lastModified;
        this.metadata = immutableMetadata(builder.metadata);
    }

    /**
     * 创建下载对象构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
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
     * 获取对象内容流。
     *
     * @return 内容流
     */
    public InputStream getContent() {
        return content;
    }

    /**
     * 获取内容长度。
     *
     * @return 非负长度；未知时为 {@code -1}
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * 获取内容类型。
     *
     * @return MIME 类型；服务端未返回时为 {@code null}
     */
    public String getContentType() {
        return contentType;
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
     * 获取最后修改时间。
     *
     * @return 最后修改时间；服务端未返回时为 {@code null}
     */
    public Instant getLastModified() {
        return lastModified;
    }

    /**
     * 获取用户元数据的不可变快照。
     *
     * @return 不可变元数据
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 关闭底层响应流并释放远程连接。
     *
     * @throws IOException 关闭响应流失败时抛出
     */
    @Override
    public void close() throws IOException {
        content.close();
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

    /**
     * 校验必填对象。
     *
     * @param value 待校验对象
     * @param fieldName 字段名称
     * @param <T> 对象类型
     * @return 已校验对象
     */
    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    /**
     * 创建元数据的不可变有序副本。
     *
     * @param metadata 原始元数据
     * @return 不可变元数据副本
     */
    private static Map<String, String> immutableMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * 下载对象构建器。
     */
    public static final class Builder {

        private String bucket;
        private String objectKey;
        private InputStream content;
        private long contentLength = OssUploadRequest.UNKNOWN_CONTENT_LENGTH;
        private String contentType;
        private String etag;
        private Instant lastModified;
        private Map<String, String> metadata = Collections.emptyMap();

        /**
         * 设置 Bucket 名称。
         *
         * @param bucket Bucket 名称
         * @return 当前构建器
         */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /**
         * 设置对象键。
         *
         * @param objectKey 对象键
         * @return 当前构建器
         */
        public Builder objectKey(String objectKey) {
            this.objectKey = objectKey;
            return this;
        }

        /**
         * 设置对象内容流。
         *
         * @param content 内容流
         * @return 当前构建器
         */
        public Builder content(InputStream content) {
            this.content = content;
            return this;
        }

        /**
         * 设置内容长度。
         *
         * @param contentLength 非负长度；未知时传 {@code -1}
         * @return 当前构建器
         */
        public Builder contentLength(long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        /**
         * 设置内容类型。
         *
         * @param contentType MIME 类型
         * @return 当前构建器
         */
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * 设置 ETag。
         *
         * @param etag 对象 ETag
         * @return 当前构建器
         */
        public Builder etag(String etag) {
            this.etag = etag;
            return this;
        }

        /**
         * 设置最后修改时间。
         *
         * @param lastModified 最后修改时间
         * @return 当前构建器
         */
        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        /**
         * 设置用户元数据。
         *
         * @param metadata 用户元数据
         * @return 当前构建器
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * 构建下载对象。
         *
         * @return 下载对象
         */
        public OssObject build() {
            return new OssObject(this);
        }
    }
}
