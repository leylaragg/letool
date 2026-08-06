package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.config.FileProperties;
import org.apache.commons.net.ftp.FTPClient;

/**
 * FTP 客户端创建扩展点。
 *
 * <p>业务项目可以替换该接口，为客户端补充代理、证书、SocketFactory 或协议特有设置。</p>
 */
@FunctionalInterface
public interface FtpClientFactory {

    /**
     * 创建尚未连接的 FTP 或 FTPS 客户端。
     *
     * @param storageType 存储类型，值为 {@code ftp} 或 {@code ftps}
     * @param properties FTP 连接配置
     * @return 尚未连接的客户端
     */
    FTPClient create(String storageType, FileProperties.Ftp properties);
}
