package com.github.leyland.letool.oss.core;

import com.github.leyland.letool.oss.config.OssProperties;
import com.github.leyland.letool.oss.exception.OssErrorCode;
import com.github.leyland.letool.oss.exception.OssException;
import com.github.leyland.letool.oss.model.OssObject;
import com.github.leyland.letool.oss.model.OssUploadRequest;
import com.github.leyland.letool.oss.model.OssUploadResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;

/**
 * 业务代码访问对象存储的统一便捷入口。
 *
 * <p>模板负责默认 Bucket、常用输入类型、参数校验和统一异常转换；具体网络行为由
 * {@link OssProvider} 及其官方 SDK 实现。</p>
 */
public class OssTemplate {

    private final OssProvider ossProvider;
    private final OssProperties properties;

    /**
     * 创建 OSS 操作模板。
     *
     * @param ossProvider 底层对象存储 Provider
     * @param properties OSS 公共配置
     */
    public OssTemplate(OssProvider ossProvider, OssProperties properties) {
        if (ossProvider == null) {
            throw new IllegalArgumentException("ossProvider 不能为空");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties 不能为空");
        }
        this.ossProvider = ossProvider;
        this.properties = properties;
    }

    /**
     * 使用默认 Bucket 上传未知长度输入流。
     *
     * @param objectKey 对象键
     * @param inputStream 输入流；调用方负责关闭
     * @return 上传结果
     */
    public OssUploadResult upload(String objectKey, InputStream inputStream) {
        return upload(
                objectKey,
                inputStream,
                OssUploadRequest.UNKNOWN_CONTENT_LENGTH,
                OssUploadRequest.DEFAULT_CONTENT_TYPE);
    }

    /**
     * 使用默认 Bucket 上传输入流。
     *
     * @param objectKey 对象键
     * @param inputStream 输入流；调用方负责关闭
     * @param contentLength 非负内容长度；未知时传 {@code -1}
     * @param contentType MIME 类型
     * @return 上传结果
     */
    public OssUploadResult upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) {
        OssUploadRequest request = OssUploadRequest.builder()
                .bucket(defaultBucket())
                .objectKey(objectKey)
                .inputStream(inputStream)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();
        return upload(request);
    }

    /**
     * 使用默认 Bucket 上传字节数组。
     *
     * @param objectKey 对象键
     * @param content 对象内容
     * @param contentType MIME 类型
     * @return 上传结果
     */
    public OssUploadResult upload(String objectKey, byte[] content, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
        return upload(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                contentType);
    }

    /**
     * 使用默认 Bucket 上传本地文件。
     *
     * <p>模板会读取文件长度和内容类型，并在 Provider 调用结束后关闭文件流。</p>
     *
     * @param objectKey 对象键
     * @param path 本地文件路径
     * @return 上传结果
     */
    public OssUploadResult upload(String objectKey, Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path 不能为空");
        }
        String bucket = defaultBucket();
        try {
            long contentLength = Files.size(path);
            String detectedContentType = Files.probeContentType(path);
            String contentType = detectedContentType == null
                    ? OssUploadRequest.DEFAULT_CONTENT_TYPE
                    : detectedContentType;
            try (InputStream inputStream = Files.newInputStream(path)) {
                return upload(bucket, objectKey, inputStream, contentLength, contentType);
            }
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(OssErrorCode.UPLOAD_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 上传完整请求。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    public OssUploadResult upload(OssUploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        try {
            OssUploadResult result = ossProvider.upload(request);
            if (result == null) {
                throw OssException.of(
                        OssErrorCode.UPLOAD_FAILED,
                        providerName(),
                        request.getBucket(),
                        request.getObjectKey());
            }
            return result;
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(
                    OssErrorCode.UPLOAD_FAILED,
                    request.getBucket(),
                    request.getObjectKey(),
                    exception);
        }
    }

    /**
     * 上传到指定 Bucket。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param inputStream 输入流；调用方负责关闭
     * @param contentLength 非负内容长度；未知时传 {@code -1}
     * @param contentType MIME 类型
     * @return 上传结果
     */
    public OssUploadResult upload(
            String bucket,
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) {
        return upload(OssUploadRequest.builder()
                .bucket(bucket)
                .objectKey(objectKey)
                .inputStream(inputStream)
                .contentLength(contentLength)
                .contentType(contentType)
                .metadata(Collections.emptyMap())
                .build());
    }

    /**
     * 使用默认 Bucket 下载对象。
     *
     * @param objectKey 对象键
     * @return 可关闭的下载对象
     */
    public OssObject download(String objectKey) {
        return download(defaultBucket(), objectKey);
    }

    /**
     * 从指定 Bucket 下载对象。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 可关闭的下载对象
     */
    public OssObject download(String bucket, String objectKey) {
        String validBucket = requireText(bucket, "bucket");
        String validObjectKey = requireText(objectKey, "objectKey");
        try {
            OssObject result = ossProvider.download(validBucket, validObjectKey);
            if (result == null) {
                throw OssException.of(
                        OssErrorCode.DOWNLOAD_FAILED,
                        providerName(),
                        validBucket,
                        validObjectKey);
            }
            return result;
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(OssErrorCode.DOWNLOAD_FAILED, validBucket, validObjectKey, exception);
        }
    }

    /**
     * 使用默认 Bucket 幂等删除对象。
     *
     * @param objectKey 对象键
     */
    public void delete(String objectKey) {
        delete(defaultBucket(), objectKey);
    }

    /**
     * 从指定 Bucket 幂等删除对象。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     */
    public void delete(String bucket, String objectKey) {
        String validBucket = requireText(bucket, "bucket");
        String validObjectKey = requireText(objectKey, "objectKey");
        try {
            ossProvider.delete(validBucket, validObjectKey);
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(OssErrorCode.DELETE_FAILED, validBucket, validObjectKey, exception);
        }
    }

    /**
     * 使用默认 Bucket 判断对象是否存在。
     *
     * @param objectKey 对象键
     * @return 对象存在时返回 {@code true}
     */
    public boolean exists(String objectKey) {
        return exists(defaultBucket(), objectKey);
    }

    /**
     * 判断指定 Bucket 中的对象是否存在。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 对象存在时返回 {@code true}
     */
    public boolean exists(String bucket, String objectKey) {
        String validBucket = requireText(bucket, "bucket");
        String validObjectKey = requireText(objectKey, "objectKey");
        try {
            return ossProvider.exists(validBucket, validObjectKey);
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(OssErrorCode.EXISTS_CHECK_FAILED, validBucket, validObjectKey, exception);
        }
    }

    /**
     * 使用默认 Bucket 生成预签名地址。
     *
     * @param objectKey 对象键
     * @param expiration 正数有效期
     * @return 预签名 URI
     */
    public URI getPresignedUrl(String objectKey, Duration expiration) {
        return getPresignedUrl(defaultBucket(), objectKey, expiration);
    }

    /**
     * 为指定 Bucket 中的对象生成预签名地址。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param expiration 正数有效期
     * @return 预签名 URI
     */
    public URI getPresignedUrl(String bucket, String objectKey, Duration expiration) {
        String validBucket = requireText(bucket, "bucket");
        String validObjectKey = requireText(objectKey, "objectKey");
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("expiration 必须为正数");
        }
        try {
            URI result = ossProvider.getPresignedUrl(validBucket, validObjectKey, expiration);
            if (result == null) {
                throw OssException.of(
                        OssErrorCode.PRESIGN_FAILED,
                        providerName(),
                        validBucket,
                        validObjectKey);
            }
            return result;
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw operationFailure(OssErrorCode.PRESIGN_FAILED, validBucket, validObjectKey, exception);
        }
    }

    /**
     * 解析快捷操作使用的默认 Bucket。
     *
     * @return 默认 Bucket
     */
    private String defaultBucket() {
        String bucket = properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw OssException.of(
                    OssErrorCode.CONFIGURATION_INVALID,
                    "请配置 letool.oss.bucket");
        }
        return bucket;
    }

    /**
     * 获取底层 Provider 标识并提供安全兜底。
     *
     * @return Provider 标识
     */
    private String providerName() {
        String providerName = ossProvider.getProviderName();
        if (providerName == null || providerName.isBlank()) {
            return "unknown";
        }
        return providerName;
    }

    /**
     * 构建保留原因链的操作异常。
     *
     * @param errorCode 操作错误码
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param cause 底层原因
     * @return OSS 异常
     */
    private OssException operationFailure(
            OssErrorCode errorCode,
            String bucket,
            String objectKey,
            Throwable cause) {
        return OssException.causedBy(errorCode, cause, providerName(), bucket, objectKey);
    }

    /**
     * 校验必填文本参数。
     *
     * @param value 待校验文本
     * @param fieldName 字段名称
     * @return 已校验文本
     */
    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
