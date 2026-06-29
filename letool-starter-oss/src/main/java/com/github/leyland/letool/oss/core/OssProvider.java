package com.github.leyland.letool.oss.core;

import java.io.InputStream;
import java.time.Duration;

/**
 * 对象存储（OSS）提供者接口，定义对象存储的标准契约。
 *
 * <p>所有对象存储实现（阿里云 OSS、MinIO、腾讯云 COS 等）都应实现此接口，
 * 以提供统一的文件上传、下载、删除、存在性检查以及预签名 URL 生成能力。
 * 上层服务（如 {@link OssTemplate}）依赖此接口而非具体实现，
 * 从而支持通过配置切换不同的对象存储后端。</p>
 *
 * <p><b>设计原则：</b></p>
 * <ul>
 *   <li>接口方法面向对象存储的通用能力，不暴露特定厂商细节</li>
 *   <li>Bucket 与 ObjectKey 作为每个方法的参数，支持多 Bucket 场景</li>
 *   <li>预签名 URL 通过 {@link Duration} 指定有效期，语义清晰</li>
 *   <li>所有 I/O 操作通过 {@link com.github.leyland.letool.oss.exception.OssException}
 *       统一异常类型进行包裹</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface OssProvider {

    // ======================== 核心操作 ========================

    /**
     * 上传文件到指定的 Bucket。
     *
     * @param bucket      目标 Bucket 名称
     * @param objectKey   对象键（即文件在 Bucket 中的路径标识）
     * @param inputStream 文件内容的输入流，调用方负责关闭
     * @param contentType 文件的 MIME 类型（如 {@code image/png}），用于设置对象元数据
     * @return 对象在存储系统中的 URL 或标识
     * @throws com.github.leyland.letool.oss.exception.OssException 上传过程中发生错误时抛出
     */
    String upload(String bucket, String objectKey, InputStream inputStream, String contentType);

    /**
     * 从指定的 Bucket 下载文件。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键（即文件在 Bucket 中的路径标识）
     * @return 文件内容的输入流，调用方负责关闭
     * @throws com.github.leyland.letool.oss.exception.OssException 下载过程中发生错误时抛出
     */
    InputStream download(String bucket, String objectKey);

    /**
     * 从指定的 Bucket 删除文件。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键（即文件在 Bucket 中的路径标识）
     * @return {@code true} 删除成功，{@code false} 文件不存在
     * @throws com.github.leyland.letool.oss.exception.OssException 删除过程中发生错误时抛出
     */
    boolean delete(String bucket, String objectKey);

    /**
     * 检查指定 Bucket 中是否存在该对象。
     *
     * @param bucket    目标 Bucket 名称
     * @param objectKey 对象键（即文件在 Bucket 中的路径标识）
     * @return {@code true} 对象存在，{@code false} 不存在
     * @throws com.github.leyland.letool.oss.exception.OssException 检查过程中发生错误时抛出
     */
    boolean exists(String bucket, String objectKey);

    /**
     * 生成对象的预签名 URL，用于临时授权的访问。
     *
     * <p>预签名 URL 允许没有凭证的客户端在有效期内访问受保护的对象。
     * 典型用途包括：生成临时下载链接、允许客户端直接上传文件到 OSS。</p>
     *
     * @param bucket     目标 Bucket 名称
     * @param objectKey  对象键（即文件在 Bucket 中的路径标识）
     * @param expiration 预签名 URL 的有效期
     * @return 预签名 URL 字符串
     * @throws com.github.leyland.letool.oss.exception.OssException 生成过程中发生错误时抛出
     */
    String getPresignedUrl(String bucket, String objectKey, Duration expiration);

    /**
     * 获取当前提供商的名称标识。
     *
     * @return 提供商名称，如 {@code "aliyun"}、{@code "minio"}、{@code "tencent-cos"}
     */
    String getProviderName();
}
