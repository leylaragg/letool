package io.github.leylaragg.letool.oss.minio;

import io.github.leylaragg.letool.oss.core.OssProvider;
import io.github.leylaragg.letool.oss.exception.OssErrorCode;
import io.github.leylaragg.letool.oss.exception.OssException;
import io.github.leylaragg.letool.oss.model.OssObject;
import io.github.leylaragg.letool.oss.model.OssUploadRequest;
import io.github.leylaragg.letool.oss.model.OssUploadResult;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteArgs;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

import java.net.URI;
import java.time.Duration;

/**
 * 基于 MinIO 官方 Java SDK 的对象存储 Provider。
 */
public final class MinioOssProvider implements OssProvider {

    /** 未知长度流式上传使用的分片大小。 */
    private static final long UNKNOWN_LENGTH_PART_SIZE = ObjectWriteArgs.MIN_MULTIPART_SIZE;

    /** S3 V4 预签名协议允许的最大有效期。 */
    private static final long MAX_PRESIGNED_EXPIRY_SECONDS = 7L * 24L * 60L * 60L;

    private final MinioClient minioClient;

    /**
     * 创建 MinIO Provider。
     *
     * @param minioClient 可复用的官方客户端
     */
    public MinioOssProvider(MinioClient minioClient) {
        if (minioClient == null) {
            throw new IllegalArgumentException("minioClient 不能为空");
        }
        this.minioClient = minioClient;
    }

    /**
     * 使用 MinIO SDK 上传对象。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    @Override
    public OssUploadResult upload(OssUploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        try {
            long partSize = request.getContentLength() == OssUploadRequest.UNKNOWN_CONTENT_LENGTH
                    ? UNKNOWN_LENGTH_PART_SIZE
                    : -1L;
            PutObjectArgs arguments = PutObjectArgs.builder()
                    .bucket(request.getBucket())
                    .object(request.getObjectKey())
                    .stream(request.getInputStream(), request.getContentLength(), partSize)
                    .contentType(request.getContentType())
                    .userMetadata(request.getMetadata())
                    .build();
            ObjectWriteResponse response = minioClient.putObject(arguments);
            return new OssUploadResult(
                    getProviderName(),
                    request.getBucket(),
                    request.getObjectKey(),
                    response.etag(),
                    response.versionId());
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(
                    OssErrorCode.UPLOAD_FAILED,
                    request.getBucket(),
                    request.getObjectKey(),
                    exception);
        }
    }

    /**
     * 使用 MinIO SDK 下载对象及元数据。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 可关闭下载对象
     */
    @Override
    public OssObject download(String bucket, String objectKey) {
        try {
            StatObjectResponse metadata = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            GetObjectResponse content = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return OssObject.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .content(content)
                    .contentLength(metadata.size())
                    .contentType(metadata.contentType())
                    .etag(metadata.etag())
                    .lastModified(metadata.lastModified() == null ? null : metadata.lastModified().toInstant())
                    .metadata(metadata.userMetadata())
                    .build();
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.DOWNLOAD_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用 MinIO SDK 幂等删除对象。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     */
    @Override
    public void delete(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.DELETE_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用 MinIO SDK 判断对象是否存在。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 对象存在时返回 {@code true}
     */
    @Override
    public boolean exists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException exception) {
            if (isObjectMissing(exception)) {
                return false;
            }
            throw failure(OssErrorCode.EXISTS_CHECK_FAILED, bucket, objectKey, exception);
        } catch (Exception exception) {
            throw failure(OssErrorCode.EXISTS_CHECK_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用 MinIO SDK 生成 GET 预签名地址。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param expiration 正数有效期
     * @return 预签名 URI
     */
    @Override
    public URI getPresignedUrl(String bucket, String objectKey, Duration expiration) {
        long seconds = expiration == null ? 0L : expiration.getSeconds();
        if (seconds <= 0L || seconds > MAX_PRESIGNED_EXPIRY_SECONDS) {
            throw OssException.of(
                    OssErrorCode.CONFIGURATION_INVALID,
                    "MinIO 预签名有效期必须在 1 秒到 7 天之间");
        }
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) seconds)
                    .build());
            return URI.create(url);
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.PRESIGN_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 获取 MinIO Provider 标识。
     *
     * @return {@code minio}
     */
    @Override
    public String getProviderName() {
        return "minio";
    }

    /**
     * 判断 SDK 异常是否表示对象不存在。
     *
     * @param exception MinIO 服务异常
     * @return 对象不存在时返回 {@code true}
     */
    private boolean isObjectMissing(ErrorResponseException exception) {
        if (exception.errorResponse() == null) {
            return false;
        }
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code)
                || "NoSuchObject".equals(code)
                || "NotFound".equals(code);
    }

    /**
     * 创建保留官方 SDK 原因链的统一异常。
     *
     * @param errorCode OSS 错误码
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param cause 官方 SDK 异常
     * @return OSS 统一异常
     */
    private OssException failure(
            OssErrorCode errorCode,
            String bucket,
            String objectKey,
            Throwable cause) {
        return OssException.causedBy(errorCode, cause, getProviderName(), bucket, objectKey);
    }
}
