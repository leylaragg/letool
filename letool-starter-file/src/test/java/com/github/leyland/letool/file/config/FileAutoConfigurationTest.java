package com.github.leyland.letool.file.config;

import com.github.leyland.letool.file.download.FileDownloadService;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.LocalFileStorage;
import com.github.leyland.letool.file.upload.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileAutoConfiguration} 的自动装配契约测试。
 *
 * <p>固定文件 starter 的默认本地存储、模块开关和业务项目自定义文件基础设施退让行为。</p>
 */
class FileAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class))
            .withPropertyValues(
                    "spring.main.allow-bean-definition-overriding=false",
                    "letool.file.storage.local.base-path=target/letool-file-test/default");

    /**
     * 验证默认配置下会注册本地存储、上传服务和下载服务。
     */
    @Test
    void shouldCreateDefaultFileBeansWhenEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileStorageProvider.class);
            assertThat(context).hasSingleBean(FileUploadService.class);
            assertThat(context).hasSingleBean(FileDownloadService.class);
            assertThat(context).hasSingleBean(FileProperties.class);
            assertThat(context.getBean(FileStorageProvider.class))
                    .isInstanceOf(LocalFileStorage.class);
        });
    }

    /**
     * 验证显式关闭文件模块时不会创建文件基础设施 Bean。
     */
    @Test
    void shouldNotCreateFileBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.file.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FileStorageProvider.class);
                    assertThat(context).doesNotHaveBean(FileUploadService.class);
                    assertThat(context).doesNotHaveBean(FileDownloadService.class);
                });
    }

    /**
     * 验证业务项目自行提供文件基础设施 Bean 时自动配置会退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesFileInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserFileConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageProvider.class);
                    assertThat(context).hasSingleBean(FileUploadService.class);
                    assertThat(context).hasSingleBean(FileDownloadService.class);
                    assertThat(context.getBean(FileStorageProvider.class))
                            .isSameAs(context.getBean("fileStorageProvider"));
                    assertThat(context.getBean(FileUploadService.class))
                            .isSameAs(context.getBean("fileUploadService"));
                    assertThat(context.getBean(FileDownloadService.class))
                            .isSameAs(context.getBean("fileDownloadService"));
                });
    }

    /**
     * 模拟业务项目自行接管文件存储、上传和下载服务。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserFileConfiguration {

        @Bean
        FileStorageProvider fileStorageProvider() {
            return new TestFileStorageProvider();
        }

        @Bean
        FileUploadService fileUploadService(FileStorageProvider fileStorageProvider) {
            return new FileUploadService(fileStorageProvider, new FileProperties());
        }

        @Bean
        FileDownloadService fileDownloadService(FileStorageProvider fileStorageProvider) {
            return new FileDownloadService(fileStorageProvider);
        }
    }

    /**
     * 用于自动装配测试的内存文件存储实现，不访问真实外部服务。
     */
    static class TestFileStorageProvider implements FileStorageProvider {

        @Override
        public String upload(InputStream inputStream, String path, String fileName) {
            return path + "/" + fileName;
        }

        @Override
        public InputStream download(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public boolean delete(String path) {
            return true;
        }

        @Override
        public boolean exists(String path) {
            return true;
        }

        @Override
        public List<FileInfo> list(String path) {
            return List.of();
        }
    }
}
