package com.github.leyland.letool.file.config;

import com.github.leyland.letool.file.download.FileDownloadService;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.FtpFileStorage;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.upload.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件模块自动配置类，负责根据 {@link FileProperties} 中的配置创建对应的存储、上传、下载服务 Bean。
 *
 * <p><b>Bean 创建策略：</b></p>
 * <ul>
 *   <li>{@code fileStorageProvider} — 根据 {@code letool.file.storage.type} 决定创建
 *       {@link LocalFileStorage} 或 {@link FtpFileStorage}。若未显式配置 Bean，则自动创建。</li>
 *   <li>{@code fileUploadService} — 封装 MultipartFile 上传逻辑的上传服务。</li>
 *   <li>{@code fileDownloadService} — 封装文件下载流式写入 HTTP Response 的下载服务。</li>
 * </ul>
 *
 * <p>通过 {@code letool.file.enabled=false} 可禁用整个模块。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FileProperties.class)
@ConditionalOnProperty(prefix = "letool.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileAutoConfiguration.class);

    // ===== Bean 定义 =====

    /**
     * 创建文件存储提供者 Bean。
     *
     * <p>根据 {@code letool.file.storage.type} 配置决定实例化哪种存储实现：
     * <ul>
     *   <li>{@code ftp} — {@link FtpFileStorage}</li>
     *   <li>{@code local} / {@code sftp}（sftp 暂回退到本地）— {@link LocalFileStorage}</li>
     * </ul>
     * 当项目中已存在同名 Bean 时，此方法不会覆盖。
     * </p>
     *
     * @param properties 文件模块配置属性
     * @return 对应存储类型的 FileStorageProvider 实现
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider fileStorageProvider(FileProperties properties) {
        String type = properties.getStorage().getType().toLowerCase();
        log.info("Initializing file storage provider: {}", type);
        switch (type) {
            case "ftp":
                return new FtpFileStorage(properties.getStorage().getFtp());
            case "sftp":
            case "local":
            default:
                return new LocalFileStorage(properties.getStorage().getLocal().getBasePath());
        }
    }

    /**
     * 创建文件上传服务 Bean。
     *
     * @param storageProvider 文件存储提供者
     * @param properties      文件模块配置属性（含上传限制等）
     * @return FileUploadService 实例
    */
    @Bean
    @ConditionalOnMissingBean
    public FileUploadService fileUploadService(FileStorageProvider storageProvider, FileProperties properties) {
        return new FileUploadService(storageProvider, properties);
    }

    /**
     * 创建文件下载服务 Bean。
     *
     * @param storageProvider 文件存储提供者
     * @return FileDownloadService 实例
    */
    @Bean
    @ConditionalOnMissingBean
    public FileDownloadService fileDownloadService(FileStorageProvider storageProvider) {
        return new FileDownloadService(storageProvider);
    }
}
