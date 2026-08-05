package com.github.leyland.letool.oss.core;

import com.github.leyland.letool.oss.model.OssObject;
import com.github.leyland.letool.oss.model.OssUploadRequest;
import com.github.leyland.letool.oss.model.OssUploadResult;

import java.net.URI;
import java.time.Duration;

/**
 * 定义对象存储 Provider 必须实现的通用生产能力。
 *
 * <p>该接口不暴露厂商客户端类型。官方 SDK 适配器和业务自定义实现都通过相同契约接入
 * {@link OssTemplate}，上层业务无需感知底层存储厂商。</p>
 */
public interface OssProvider {

    /**
     * 上传对象。
     *
     * <p>Provider 不负责关闭请求中的输入流，输入流生命周期由调用方管理。</p>
     *
     * @param request 已校验的上传请求
     * @return 对象身份和服务端版本信息
     */
    OssUploadResult upload(OssUploadRequest request);

    /**
     * 下载对象。
     *
     * <p>返回对象持有远程响应流，调用方必须关闭。</p>
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 可关闭的下载对象
     */
    OssObject download(String bucket, String objectKey);

    /**
     * 幂等删除对象。
     *
     * <p>目标对象不存在时也视为删除完成，不执行额外的存在性查询。</p>
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     */
    void delete(String bucket, String objectKey);

    /**
     * 判断对象是否存在。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @return 对象存在时返回 {@code true}
     */
    boolean exists(String bucket, String objectKey);

    /**
     * 生成临时访问对象的预签名地址。
     *
     * @param bucket Bucket 名称
     * @param objectKey 对象键
     * @param expiration 正数有效期
     * @return 预签名 URI
     */
    URI getPresignedUrl(String bucket, String objectKey, Duration expiration);

    /**
     * 获取 Provider 的稳定标识。
     *
     * @return Provider 标识
     */
    String getProviderName();
}
