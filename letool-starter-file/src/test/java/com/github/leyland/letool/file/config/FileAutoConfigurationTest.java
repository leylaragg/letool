package com.github.leyland.letool.file.config;

import com.github.leyland.letool.file.core.FileTemplate;
import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.resumable.LocalUploadSessionRepository;
import com.github.leyland.letool.file.resumable.ResumableUploadCleaner;
import com.github.leyland.letool.file.resumable.ResumableUploadService;
import com.github.leyland.letool.file.resumable.UploadSessionRepository;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.FtpFileStorage;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.transfer.TransferProgressMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件 Starter 的关键自动配置契约测试。
 */
class FileAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class))
            .withPropertyValues("letool.file.storage.local.base-path=target/letool-file-test/default");

    /**
     * 验证默认配置提供本地存储和统一文件门面。
     */
    @Test
    void shouldCreateDefaultLocalStorageAndFacade() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileStorageProvider.class);
            assertThat(context).hasSingleBean(FileTemplate.class);
            assertThat(context).hasSingleBean(TransferProgressMonitor.class);
            assertThat(context).doesNotHaveBean(ResumableUploadService.class);
            assertThat(context.getBean(FileStorageProvider.class)).isInstanceOf(LocalFileStorage.class);
        });
    }

    /**
     * 验证显式启用断点续传后创建默认仓库、服务和可关闭清理器。
     */
    @Test
    void shouldCreateResumableComponentsWhenEnabled() {
        contextRunner.withPropertyValues(
                        "letool.file.resumable.enabled=true",
                        "letool.file.resumable.temporary-path=target/letool-file-test/sessions")
                .run(context -> {
                    assertThat(context).hasSingleBean(UploadSessionRepository.class);
                    assertThat(context.getBean(UploadSessionRepository.class))
                            .isInstanceOf(LocalUploadSessionRepository.class);
                    assertThat(context).hasSingleBean(ResumableUploadService.class);
                    assertThat(context).hasSingleBean(ResumableUploadCleaner.class);
                });
    }

    /**
     * 验证显式关闭模块后不会向业务容器注册文件 Bean。
     */
    @Test
    void shouldNotCreateFileBeansWhenDisabled() {
        contextRunner.withPropertyValues("letool.file.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageProvider.class);
                    assertThat(context).doesNotHaveBean(FileTemplate.class);
                });
    }

    /**
     * 验证选择 FTPS 时创建安全传输 Provider，且启动阶段不会建立远程连接。
     */
    @Test
    void shouldCreateSecureFtpsStorage() {
        contextRunner.withPropertyValues(
                        "letool.file.storage.type=ftps",
                        "letool.file.storage.ftp.username=test-user",
                        "letool.file.storage.ftp.password=test-password")
                .run(context -> {
                    FileStorageProvider provider = context.getBean(FileStorageProvider.class);
                    assertThat(provider).isInstanceOf(FtpFileStorage.class);
                    assertThat(provider.capabilities()).contains(StorageCapability.SECURE_TRANSPORT);
                });
    }

    /**
     * 验证未知存储类型不能静默回退到本地存储。
     */
    @Test
    void shouldFailFastForUnknownStorageType() {
        contextRunner.withPropertyValues("letool.file.storage.type=sftp")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证用户提供存储实现时默认实现会退让。
     */
    @Test
    void shouldBackOffForUserStorageProvider() {
        contextRunner.withUserConfiguration(UserStorageConfiguration.class)
                .run(context -> assertThat(context.getBean(FileStorageProvider.class))
                        .isSameAs(context.getBean("customFileStorageProvider")));
    }

    /**
     * 业务项目自定义存储配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserStorageConfiguration {

        /**
         * 创建测试自定义存储。
         *
         * @return 自定义存储实现
         */
        @Bean
        FileStorageProvider customFileStorageProvider() {
            return new TestFileStorageProvider();
        }
    }

    /**
     * 用于验证自动配置退让的最小存储实现。
     */
    private static final class TestFileStorageProvider implements FileStorageProvider {

        @Override
        public StoredFile store(StoreRequest request, InputStream inputStream) {
            return new StoredFile(request.key(), request.originalName(), "file.txt", 0,
                    request.contentType(), null, Instant.EPOCH);
        }

        @Override
        public FileResource open(String key) {
            FileMetadata metadata = stat(key);
            return new FileResource(metadata, new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public boolean delete(String key) {
            return true;
        }

        @Override
        public boolean exists(String key) {
            return true;
        }

        @Override
        public FileMetadata stat(String key) {
            return new FileMetadata(key, "file.txt", 0, false, Instant.EPOCH,
                    "application/octet-stream");
        }

        @Override
        public List<FileMetadata> list(String directory) {
            return List.of();
        }

        @Override
        public Set<com.github.leyland.letool.file.model.StorageCapability> capabilities() {
            return Set.of();
        }
    }
}
