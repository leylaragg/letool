package com.github.leyland.letool.file.config;

import com.github.leyland.letool.file.core.FileTemplate;
import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.FtpClientFactory;
import com.github.leyland.letool.file.storage.FtpFileStorage;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.validation.FileTypeDetector;
import com.github.leyland.letool.file.validation.FileValidationPolicy;
import com.github.leyland.letool.file.validation.MagicNumberFileTypeDetector;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Locale;

/**
 * 文件模块自动配置，提供默认存储实现、校验扩展和统一业务门面。
 */
@AutoConfiguration
@EnableConfigurationProperties(FileProperties.class)
@ConditionalOnClass(FileTemplate.class)
@ConditionalOnProperty(prefix = "letool.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileAutoConfiguration.class);

    /**
     * 创建默认 FTP 客户端工厂。
     *
     * @return FTP 与 FTPS 客户端工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public FtpClientFactory ftpClientFactory() {
        return (storageType, properties) -> {
            if ("ftps".equalsIgnoreCase(storageType)) {
                FTPSClient client = new FTPSClient(
                        properties.getProtocol(), properties.isImplicit());
                client.setEndpointCheckingEnabled(properties.isEndpointCheckingEnabled());
                return client;
            }
            return new FTPClient();
        };
    }

    /**
     * 根据显式存储类型创建默认 Provider，未知类型直接终止启动。
     *
     * @param properties 文件配置
     * @param ftpClientFactory FTP 客户端工厂
     * @return Local、FTP 或 FTPS 存储实现
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider fileStorageProvider(
            FileProperties properties,
            FtpClientFactory ftpClientFactory) {
        String configuredType = properties.getStorage().getType();
        if (configuredType == null || configuredType.isBlank()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "storage.type");
        }
        String storageType = configuredType.trim().toLowerCase(Locale.ROOT);
        return switch (storageType) {
            case "local" -> new LocalFileStorage(properties.getStorage().getLocal().getBasePath());
            case "ftp" -> {
                log.warn("当前文件存储使用明文 FTP；生产环境建议改用 FTPS 或自定义安全 Provider");
                yield new FtpFileStorage(
                        properties.getStorage().getFtp(), ftpClientFactory, false);
            }
            case "ftps" -> new FtpFileStorage(
                    properties.getStorage().getFtp(), ftpClientFactory, true);
            default -> throw FileException.of(
                    FileErrorCode.CONFIGURATION_INVALID, "storage.type");
        };
    }

    /**
     * 创建默认轻量文件类型探测器。
     *
     * @return 默认魔数探测器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileTypeDetector fileTypeDetector() {
        return new MagicNumberFileTypeDetector();
    }

    /**
     * 创建业务代码主要使用的统一文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param properties 文件配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 用户上传校验策略
     * @return 文件操作门面
     */
    @Bean
    @ConditionalOnMissingBean
    public FileTemplate fileTemplate(
            FileStorageProvider storageProvider,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            ObjectProvider<FileValidationPolicy> validationPolicies) {
        List<FileValidationPolicy> orderedPolicies = validationPolicies.orderedStream().toList();
        return new FileTemplate(
                storageProvider, properties, fileTypeDetector, orderedPolicies);
    }
}
