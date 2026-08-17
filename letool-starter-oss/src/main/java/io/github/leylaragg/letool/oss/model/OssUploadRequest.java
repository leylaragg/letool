package io.github.leylaragg.letool.oss.model;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述一次对象上传请求。
 *
 * <p>请求对象只保存输入流引用，不负责关闭输入流。调用方应在上传结束后关闭自己创建的流。</p>
 */
public final class OssUploadRequest {

    /** 未知内容长度。 */
    public static final long UNKNOWN_CONTENT_LENGTH = -1L;

    /** 默认二进制内容类型。 */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String bucket;
    private final String objectKey;
    private final InputStream inputStream;
    private final long contentLength;
    private final String contentType;
    private final Map<String, String> metadata;

    /**
     * 使用构建器创建不可变上传请求。
     *
     * @param builder 已完成参数设置的构建器
     */
    private OssUploadRequest(Builder builder) {
        this.bucket = requireText(builder.bucket, "bucket");
        this.objectKey = requireText(builder.objectKey, "objectKey");
        this.inputStream = requireValue(builder.inputStream, "inputStream");
        if (builder.contentLength < UNKNOWN_CONTENT_LENGTH) {
            throw new IllegalArgumentException("contentLength must be -1 or greater");
        }
        this.contentLength = builder.contentLength;
        this.contentType = requireText(builder.contentType, "contentType");
        this.metadata = immutableMetadata(builder.metadata);
    }

    /**
     * 创建上传请求构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取目标 Bucket。
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
     * 获取待上传内容流。
     *
     * @return 输入流
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * 获取内容长度。
     *
     * @return 非负长度；{@code -1} 表示未知
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * 获取内容类型。
     *
     * @return MIME 类型
     */
    public String getContentType() {
        return contentType;
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
     * 上传请求构建器。
     */
    public static final class Builder {

        private String bucket;
        private String objectKey;
        private InputStream inputStream;
        private long contentLength = UNKNOWN_CONTENT_LENGTH;
        private String contentType = DEFAULT_CONTENT_TYPE;
        private Map<String, String> metadata = Collections.emptyMap();

        /**
         * 设置目标 Bucket。
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
         * 设置上传内容流。
         *
         * @param inputStream 输入流
         * @return 当前构建器
         */
        public Builder inputStream(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        /**
         * 设置内容长度。
         *
         * @param contentLength 非负长度；{@code -1} 表示未知
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
         * 构建不可变上传请求。
         *
         * @return 上传请求
         */
        public OssUploadRequest build() {
            return new OssUploadRequest(this);
        }
    }
}
