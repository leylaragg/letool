package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileResource;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FTP 下载流和连接生命周期测试。
 */
class FtpFileStorageTest {

    /**
     * 验证下载流关闭后才完成 FTP 命令并断开连接。
     *
     * @throws Exception 下载流读取失败时抛出
     */
    @Test
    void shouldCompletePendingCommandWhenResourceCloses() throws Exception {
        TrackingFtpClient client = new TrackingFtpClient();
        FtpFileStorage storage = new FtpFileStorage(
                ftpProperties(), (type, properties) -> client, false);

        FileResource resource = storage.open("docs/note.txt");

        assertThat(client.completePendingCommandCalled).isFalse();
        assertThat(resource.inputStream().readAllBytes())
                .isEqualTo("ftp-content".getBytes(StandardCharsets.UTF_8));
        resource.close();
        assertThat(client.completePendingCommandCalled).isTrue();
        assertThat(client.disconnected).isTrue();
    }

    /**
     * 验证网络故障不会被存在性检查吞掉并转换为不存在。
     */
    @Test
    void shouldNotConvertFtpFailureToMissingFile() {
        TrackingFtpClient client = new TrackingFtpClient();
        client.listFailure = new IOException("网络不可用");
        FtpFileStorage storage = new FtpFileStorage(
                ftpProperties(), (type, properties) -> client, false);

        assertThatThrownBy(() -> storage.exists("docs/note.txt"))
                .isInstanceOf(FileException.class)
                .extracting("code")
                .isEqualTo("FILE_005");
    }

    /**
     * 创建测试使用的 FTP 配置。
     *
     * @return FTP 配置
     */
    private FileProperties.Ftp ftpProperties() {
        FileProperties.Ftp properties = new FileProperties.Ftp();
        properties.setUsername("tester");
        properties.setPassword("secret");
        return properties;
    }

    /**
     * 不访问网络的 FTP 客户端，用于观察资源释放行为。
     */
    private static final class TrackingFtpClient extends FTPClient {
        private boolean connected;
        private boolean disconnected;
        private boolean completePendingCommandCalled;
        private IOException listFailure;

        @Override
        public void connect(String hostname, int port) {
            connected = true;
        }

        @Override
        public boolean login(String username, String password) {
            return true;
        }

        @Override
        public boolean setFileType(int fileType) {
            return true;
        }

        @Override
        public boolean isConnected() {
            return connected && !disconnected;
        }

        @Override
        public FTPFile[] listFiles(String pathname) throws IOException {
            if (listFailure != null) {
                throw listFailure;
            }
            FTPFile file = new FTPFile();
            file.setName("note.txt");
            file.setSize("ftp-content".getBytes(StandardCharsets.UTF_8).length);
            file.setType(FTPFile.FILE_TYPE);
            return new FTPFile[]{file};
        }

        @Override
        public InputStream retrieveFileStream(String remote) {
            return new ByteArrayInputStream("ftp-content".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean completePendingCommand() {
            completePendingCommandCalled = true;
            return true;
        }

        @Override
        public boolean logout() {
            return true;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }
    }
}
