package io.github.leylaragg.letool.oss.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import io.github.leylaragg.letool.oss.core.OssProvider;
import io.github.leylaragg.letool.oss.exception.OssErrorCode;
import io.github.leylaragg.letool.oss.exception.OssException;
import io.github.leylaragg.letool.oss.model.OssObject;
import io.github.leylaragg.letool.oss.model.OssUploadRequest;
import io.github.leylaragg.letool.oss.model.OssUploadResult;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * 基于阿里云 OSS 官方 Java SDK 的对象存储 Provider。
 */
public final class AliyunOssProvider implements OssProvider {

    private final OSS ossClient;

    /**
     * 创建阿里云 OSS Provider。
     *
     * @param ossClient 可复用的官方客户端
     */
    public AliyunOssProvider(OSS ossClient) {
        if (ossClient == null) {
            throw new IllegalArgumentException("ossClient 不能为空");
        }
        this.ossClient = ossClient;
    }

    /**
     * 使用阿里云 SDK 上传对象。
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
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(request.getContentType());
            metadata.setUserMetadata(request.getMetadata());
            if (request.getContentLength() >= 0L) {
                metadata.setContentLength(request.getContentLength());
            }
            PutObjectRequest sdkRequest = new PutObjectRequest(
                    request.getBucket(),
                    request.getObjectKey(),
                    request.getInputStream(),
                    metadata);
            PutObjectResult response = ossClient.putObject(sdkRequest);
            return new OssUploadResult(
                    getProviderName(),
                    request.getBucket(),
                    request.getObjectKey(),
                    response.getETag(),
                    response.getVersionId());
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
     * 使用阿里云 SDK 下载对象和元数据。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 可关闭下载对象
     */
    @Override
    public OssObject download(String bucket, String objectKey) {
        try {
            OSSObject response = ossClient.getObject(bucket, objectKey);
            ObjectMetadata metadata = response.getObjectMetadata();
            return OssObject.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .content(response.getObjectContent())
                    .contentLength(metadata == null ? -1L : metadata.getContentLength())
                    .contentType(metadata == null ? null : metadata.getContentType())
                    .etag(metadata == null ? null : metadata.getETag())
                    .lastModified(toInstant(metadata == null ? null : metadata.getLastModified()))
                    .metadata(metadata == null ? Collections.emptyMap() : safeMetadata(metadata.getUserMetadata()))
                    .build();
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.DOWNLOAD_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用阿里云 SDK 幂等删除对象。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     */
    @Override
    public void delete(String bucket, String objectKey) {
        try {
            ossClient.deleteObject(bucket, objectKey);
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.DELETE_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用阿里云 SDK 判断对象是否存在。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 对象存在时返回 {@code true}
     */
    @Override
    public boolean exists(String bucket, String objectKey) {
        try {
            return ossClient.doesObjectExist(bucket, objectKey);
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.EXISTS_CHECK_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 使用阿里云 SDK 生成 GET 预签名地址。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param expiration 正数有效期
     * @return 预签名 URI
     */
    @Override
    public URI getPresignedUrl(String bucket, String objectKey, Duration expiration) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw OssException.of(OssErrorCode.CONFIGURATION_INVALID, "预签名有效期必须为正数");
        }
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    bucket,
                    objectKey,
                    HttpMethod.GET);
            request.setExpiration(Date.from(Instant.now().plus(expiration)));
            URL url = ossClient.generatePresignedUrl(request);
            return url.toURI();
        } catch (OssException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(OssErrorCode.PRESIGN_FAILED, bucket, objectKey, exception);
        }
    }

    /**
     * 获取阿里云 Provider 标识。
     *
     * @return {@code aliyun}
     */
    @Override
    public String getProviderName() {
        return "aliyun";
    }

    /**
     * 将旧日期对象转换为不可变时间点。
     *
     * @param date 日期；可为 {@code null}
     * @return 时间点；日期为空时为 {@code null}
     */
    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /**
     * 安全处理 SDK 可能返回的空元数据。
     *
     * @param metadata SDK 用户元数据
     * @return 非空元数据
     */
    private Map<String, String> safeMetadata(Map<String, String> metadata) {
        return metadata == null ? Collections.emptyMap() : metadata;
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
