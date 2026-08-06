package com.github.leyland.letool.file.config;

import com.github.leyland.letool.file.core.FileTemplate;
import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.resumable.LocalUploadSessionRepository;
import com.github.leyland.letool.file.resumable.ResumableUploadCleaner;
import com.github.leyland.letool.file.resumable.ResumableUploadService;
import com.github.leyland.letool.file.resumable.UploadSessionRepository;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.FtpClientFactory;
import com.github.leyland.letool.file.storage.FtpFileStorage;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.transfer.InMemoryTransferProgressMonitor;
import com.github.leyland.letool.file.transfer.TransferProgressListener;
import com.github.leyland.letool.file.transfer.TransferProgressMonitor;
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

import java.nio.file.Path;
import java.time.Duration;
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
     * 创建有容量、保留时间和通知采样边界的默认进度监视器。
     *
     * @param properties 文件配置
     * @param listeners 用户进度监听器
     * @return 传输进度监视器
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferProgressMonitor transferProgressMonitor(
            FileProperties properties,
            ObjectProvider<TransferProgressListener> listeners) {
        FileProperties.Progress progress = properties.getProgress();
        validateProgressConfiguration(progress);
        return new InMemoryTransferProgressMonitor(
                progress.getRetention(),
                progress.getMaxEntries(),
                progress.getNotificationInterval(),
                progress.getNotificationBytes().toBytes(),
                listeners.orderedStream().toList());
    }

    /**
     * 创建业务代码主要使用的统一文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param properties 文件配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 用户上传校验策略
     * @param progressMonitor 传输进度监视器
     * @return 文件操作门面
     */
    @Bean
    @ConditionalOnMissingBean
    public FileTemplate fileTemplate(
            FileStorageProvider storageProvider,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            ObjectProvider<FileValidationPolicy> validationPolicies,
            TransferProgressMonitor progressMonitor) {
        List<FileValidationPolicy> orderedPolicies = validationPolicies.orderedStream().toList();
        return new FileTemplate(
                storageProvider,
                properties,
                fileTypeDetector,
                orderedPolicies,
                progressMonitor);
    }

    /**
     * 创建默认本地会话仓库；业务提供分布式仓库 Bean 时自动退让。
     *
     * @param properties 文件配置
     * @return 本地会话仓库
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.file.resumable",
            name = "enabled",
            havingValue = "true")
    public UploadSessionRepository uploadSessionRepository(FileProperties properties) {
        String temporaryPath = properties.getResumable().getTemporaryPath();
        if (temporaryPath == null || temporaryPath.isBlank()) {
            throw FileException.of(
                    FileErrorCode.CONFIGURATION_INVALID,
                    "resumable.temporary-path");
        }
        return new LocalUploadSessionRepository(Path.of(temporaryPath));
    }

    /**
     * 创建连续分片断点续传服务。
     *
     * @param repository 会话仓库
     * @param storageProvider 最终文件存储提供者
     * @param progressMonitor 传输进度监视器
     * @param properties 文件配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 用户上传校验策略
     * @return 断点续传服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.file.resumable",
            name = "enabled",
            havingValue = "true")
    public ResumableUploadService resumableUploadService(
            UploadSessionRepository repository,
            FileStorageProvider storageProvider,
            TransferProgressMonitor progressMonitor,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            ObjectProvider<FileValidationPolicy> validationPolicies) {
        return new ResumableUploadService(
                repository,
                storageProvider,
                progressMonitor,
                properties,
                fileTypeDetector,
                validationPolicies.orderedStream().toList());
    }

    /**
     * 创建随应用生命周期关闭的过期会话清理器。
     *
     * @param service 断点续传服务
     * @param properties 文件配置
     * @return 过期会话清理器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.file.resumable",
            name = "enabled",
            havingValue = "true")
    public ResumableUploadCleaner resumableUploadCleaner(
            ResumableUploadService service,
            FileProperties properties) {
        return new ResumableUploadCleaner(
                service, properties.getResumable().getCleanupInterval());
    }

    /**
     * 在创建默认监视器前校验进度配置。
     *
     * @param progress 进度配置
     */
    private void validateProgressConfiguration(FileProperties.Progress progress) {
        Duration retention = progress.getRetention();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "progress.retention");
        }
        if (progress.getMaxEntries() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "progress.max-entries");
        }
        Duration interval = progress.getNotificationInterval();
        if (interval == null || interval.isNegative()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "progress.notification-interval");
        }
        if (progress.getNotificationBytes() == null
                || progress.getNotificationBytes().toBytes() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID,
                    "progress.notification-bytes");
        }
    }
}
