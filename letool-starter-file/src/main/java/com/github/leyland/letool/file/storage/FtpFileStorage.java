package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.OverwritePolicy;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.util.MimeTypeUtil;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Apache Commons Net 的流式 FTP 与 FTPS 文件存储。
 */
public final class FtpFileStorage implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(FtpFileStorage.class);

    private final FileProperties.Ftp properties;
    private final FtpClientFactory clientFactory;
    private final boolean secureTransport;
    private final String storageType;
    private final String basePath;

    /**
     * 使用默认客户端工厂创建明文 FTP 存储。
     *
     * @param properties FTP 配置
     */
    public FtpFileStorage(FileProperties.Ftp properties) {
        this(properties, FtpFileStorage::createDefaultClient, false);
    }

    /**
     * 创建可测试、可扩展的 FTP 或 FTPS 存储。
     *
     * @param properties FTP 配置
     * @param clientFactory 客户端创建工厂
     * @param secureTransport 是否使用 FTPS 安全传输
     */
    public FtpFileStorage(
            FileProperties.Ftp properties,
            FtpClientFactory clientFactory,
            boolean secureTransport) {
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory 不能为空");
        this.secureTransport = secureTransport;
        this.storageType = secureTransport ? "ftps" : "ftp";
        this.basePath = normalizeBasePath(properties.getBasePath());
        validateProperties(properties);
    }

    /**
     * 使用远程临时文件写入并在成功后重命名。
     *
     * @param request 写入请求
     * @param inputStream 文件输入流
     * @return 实际存储结果
     */
    @Override
    public StoredFile store(StoreRequest request, InputStream inputStream) {
        Objects.requireNonNull(request, "request 不能为空");
        if (inputStream == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "inputStream");
        }
        String key = StorageKey.file(request.key());
        String target = remotePath(key);
        String temporary = target + ".upload-" + UUID.randomUUID();
        FTPClient client = connect();
        try {
            ensureDirectories(client, parentPath(target));
            if (request.overwritePolicy() == OverwritePolicy.FAIL && remoteExists(client, target)) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "目标文件已存在");
            }
            CountingDigestInputStream countingInputStream = new CountingDigestInputStream(inputStream);
            if (!client.storeFile(temporary, countingInputStream)) {
                throw protocolFailure("FTP 上传临时文件失败", client);
            }
            if (request.declaredSize() >= 0 && request.declaredSize() != countingInputStream.count()) {
                client.deleteFile(temporary);
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "声明大小与实际大小不一致");
            }
            if (request.overwritePolicy() == OverwritePolicy.REPLACE && remoteExists(client, target)) {
                if (!client.deleteFile(target)) {
                    throw protocolFailure("FTP 无法替换已有文件", client);
                }
            }
            if (!client.rename(temporary, target)) {
                throw protocolFailure("FTP 临时文件重命名失败", client);
            }
            return new StoredFile(
                    key,
                    request.originalName(),
                    fileName(key),
                    countingInputStream.count(),
                    request.contentType(),
                    countingInputStream.sha256(),
                    Instant.now());
        } catch (FileException exception) {
            deleteRemoteTemporary(client, temporary);
            throw exception;
        } catch (IOException exception) {
            deleteRemoteTemporary(client, temporary);
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            disconnect(client);
        }
    }

    /**
     * 打开与 FTP 连接生命周期绑定的远程输入流。
     *
     * @param key 文件逻辑键
     * @return 可关闭文件资源
     */
    @Override
    public FileResource open(String key) {
        String normalizedKey = StorageKey.file(key);
        String remotePath = remotePath(normalizedKey);
        FTPClient client = connect();
        try {
            FileMetadata metadata = readMetadata(client, normalizedKey, remotePath);
            if (metadata.directory()) {
                throw FileException.of(FileErrorCode.PARAMETER_INVALID, "key 必须指向文件");
            }
            InputStream dataStream = client.retrieveFileStream(remotePath);
            if (dataStream == null) {
                throw protocolFailure("FTP 无法打开下载流", client);
            }
            return new FileResource(metadata, new CompletingFtpInputStream(dataStream, client, false));
        } catch (FileException exception) {
            disconnect(client);
            throw exception;
        } catch (IOException exception) {
            disconnect(client);
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 使用 FTP REST 偏移打开固定长度的远程文件区间。
     *
     * @param key 文件逻辑键
     * @param start 起始字节位置
     * @param length 区间长度
     * @return 可关闭区间资源
     */
    @Override
    public FileResource openRange(String key, long start, long length) {
        String normalizedKey = StorageKey.file(key);
        String remotePath = remotePath(normalizedKey);
        FTPClient client = connect();
        try {
            FileMetadata metadata = readMetadata(client, normalizedKey, remotePath);
            validateRange(metadata, start, length);
            client.setRestartOffset(start);
            InputStream dataStream = client.retrieveFileStream(remotePath);
            if (dataStream == null) {
                throw FileException.of(FileErrorCode.CAPABILITY_UNSUPPORTED, "ftp-range-read");
            }
            InputStream completing = new CompletingFtpInputStream(dataStream, client, true);
            return new FileResource(metadata, new RangeInputStream(completing, length));
        } catch (FileException exception) {
            disconnect(client);
            throw exception;
        } catch (IOException exception) {
            disconnect(client);
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 删除远程文件。
     *
     * @param key 文件逻辑键
     * @return 是否成功删除
     */
    @Override
    public boolean delete(String key) {
        FTPClient client = connect();
        try {
            String remotePath = remotePath(StorageKey.file(key));
            if (!remoteExists(client, remotePath)) {
                return false;
            }
            if (!client.deleteFile(remotePath)) {
                throw protocolFailure("FTP 删除文件失败", client);
            }
            return true;
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            disconnect(client);
        }
    }

    /**
     * 检查远程文件是否存在。
     *
     * @param key 文件逻辑键
     * @return 文件是否存在
     */
    @Override
    public boolean exists(String key) {
        FTPClient client = connect();
        try {
            return remoteExists(client, remotePath(StorageKey.file(key)));
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            disconnect(client);
        }
    }

    /**
     * 查询远程文件元数据。
     *
     * @param key 文件逻辑键
     * @return 文件元数据
     */
    @Override
    public FileMetadata stat(String key) {
        String normalizedKey = StorageKey.file(key);
        FTPClient client = connect();
        try {
            return readMetadata(client, normalizedKey, remotePath(normalizedKey));
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            disconnect(client);
        }
    }

    /**
     * 列出远程目录直接子项。
     *
     * @param directory 目录逻辑键
     * @return 按逻辑键排序的元数据列表
     */
    @Override
    public List<FileMetadata> list(String directory) {
        String normalizedDirectory = StorageKey.directory(directory);
        FTPClient client = connect();
        try {
            FTPFile[] files = client.listFiles(remotePath(normalizedDirectory));
            if (files == null) {
                throw protocolFailure("FTP 列举目录失败", client);
            }
            List<FileMetadata> result = new ArrayList<>();
            for (FTPFile file : files) {
                if (".".equals(file.getName()) || "..".equals(file.getName())) {
                    continue;
                }
                String childKey = normalizedDirectory.isEmpty()
                        ? file.getName()
                        : normalizedDirectory + "/" + file.getName();
                result.add(toMetadata(childKey, file));
            }
            result.sort(Comparator.comparing(FileMetadata::key));
            return List.copyOf(result);
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        } finally {
            disconnect(client);
        }
    }

    /**
     * 返回远程存储支持的能力。
     *
     * @return FTP 或 FTPS 能力集合
     */
    @Override
    public Set<StorageCapability> capabilities() {
        return secureTransport
                ? Set.of(
                        StorageCapability.DIRECTORY_LISTING,
                        StorageCapability.RANGE_READ,
                        StorageCapability.SECURE_TRANSPORT)
                : Set.of(StorageCapability.DIRECTORY_LISTING, StorageCapability.RANGE_READ);
    }

    /**
     * 校验区间位于完整远程文件范围内。
     *
     * @param metadata 完整文件元数据
     * @param start 起始字节位置
     * @param length 区间长度
     */
    private void validateRange(FileMetadata metadata, long start, long length) {
        if (metadata.directory() || start < 0 || length <= 0
                || start >= metadata.size() || length > metadata.size() - start) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "range");
        }
    }

    /**
     * 建立并初始化 FTP 连接。
     *
     * @return 已登录客户端
     */
    private FTPClient connect() {
        FTPClient client = clientFactory.create(storageType, properties);
        if (client == null) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "ftp.client-factory");
        }
        try {
            configureClient(client);
            client.connect(properties.getHost(), properties.getPort());
            if (client.getReplyCode() > 0 && !FTPReply.isPositiveCompletion(client.getReplyCode())) {
                throw protocolFailure("FTP 服务器拒绝连接", client);
            }
            if (!client.login(properties.getUsername(), properties.getPassword())) {
                throw protocolFailure("FTP 登录失败", client);
            }
            if (client instanceof FTPSClient ftpsClient) {
                ftpsClient.execPBSZ(0);
                ftpsClient.execPROT("P");
            }
            if (properties.isPassiveMode()) {
                client.enterLocalPassiveMode();
            } else {
                client.enterLocalActiveMode();
            }
            if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
                throw protocolFailure("FTP 无法启用二进制传输", client);
            }
            return client;
        } catch (FileException exception) {
            disconnect(client);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            disconnect(client);
            throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
        }
    }

    /**
     * 应用连接和数据超时配置。
     *
     * @param client 待配置客户端
     */
    private void configureClient(FTPClient client) {
        client.setConnectTimeout(toIntMilliseconds(properties.getConnectTimeout(), "ftp.connect-timeout"));
        client.setDefaultTimeout(toIntMilliseconds(properties.getDefaultTimeout(), "ftp.default-timeout"));
        client.setDataTimeout(properties.getDataTimeout());
        client.setControlKeepAliveTimeout(properties.getKeepAliveInterval());
        client.setCharset(Charset.forName(properties.getCharset()));
    }

    /**
     * 读取指定远程路径的元数据。
     *
     * @param client 已连接客户端
     * @param key 逻辑键
     * @param remotePath 远程绝对路径
     * @return 文件元数据
     * @throws IOException FTP 命令失败时抛出
     */
    private FileMetadata readMetadata(FTPClient client, String key, String remotePath) throws IOException {
        FTPFile[] files = client.listFiles(remotePath);
        if (files == null) {
            throw protocolFailure("FTP 查询元数据失败", client);
        }
        if (files.length == 0) {
            throw FileException.of(FileErrorCode.FILE_NOT_FOUND);
        }
        return toMetadata(key, files[0]);
    }

    /**
     * 判断远程绝对路径是否存在。
     *
     * @param client 已连接客户端
     * @param remotePath 远程绝对路径
     * @return 路径是否存在
     * @throws IOException FTP 命令失败时抛出
     */
    private boolean remoteExists(FTPClient client, String remotePath) throws IOException {
        FTPFile[] files = client.listFiles(remotePath);
        if (files == null) {
            throw protocolFailure("FTP 检查文件存在性失败", client);
        }
        return files.length > 0;
    }

    /**
     * 逐级创建远程目录并校验每条命令结果。
     *
     * @param client 已连接客户端
     * @param absoluteDirectory 远程绝对目录
     * @throws IOException FTP 命令失败时抛出
     */
    private void ensureDirectories(FTPClient client, String absoluteDirectory) throws IOException {
        if (!client.changeWorkingDirectory("/")) {
            throw protocolFailure("FTP 无法进入根目录", client);
        }
        for (String segment : absoluteDirectory.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (!client.changeWorkingDirectory(segment)) {
                if (!client.makeDirectory(segment) || !client.changeWorkingDirectory(segment)) {
                    throw protocolFailure("FTP 创建目录失败", client);
                }
            }
        }
    }

    /**
     * 将 FTP 文件转换为统一元数据。
     *
     * @param key 逻辑键
     * @param file FTP 文件信息
     * @return 统一文件元数据
     */
    private FileMetadata toMetadata(String key, FTPFile file) {
        boolean directory = file.isDirectory();
        Instant lastModified = file.getTimestamp() == null
                ? Instant.EPOCH
                : file.getTimestamp().toInstant();
        return new FileMetadata(
                key,
                file.getName() == null || file.getName().isBlank() ? fileName(key) : file.getName(),
                directory ? 0 : Math.max(0, file.getSize()),
                directory,
                lastModified,
                directory ? "application/octet-stream" : MimeTypeUtil.getMimeType(file.getName()));
    }

    /**
     * 将逻辑键映射到配置的远程根目录。
     *
     * @param key 已规范化逻辑键
     * @return 远程绝对路径
     */
    private String remotePath(String key) {
        if (key == null || key.isEmpty()) {
            return basePath;
        }
        return "/".equals(basePath) ? "/" + key : basePath + "/" + key;
    }

    /**
     * 从远程文件路径提取父目录。
     *
     * @param path 远程文件路径
     * @return 远程父目录
     */
    private String parentPath(String path) {
        int index = path.lastIndexOf('/');
        return index <= 0 ? "/" : path.substring(0, index);
    }

    /**
     * 从逻辑键提取文件名。
     *
     * @param key 文件逻辑键
     * @return 文件名
     */
    private String fileName(String key) {
        int index = key.lastIndexOf('/');
        return index < 0 ? key : key.substring(index + 1);
    }

    /**
     * 创建不向外暴露服务器回复内容的协议异常。
     *
     * @param operation 安全诊断描述
     * @param client FTP 客户端
     * @return 包含原始回复作为内部 cause 的文件异常
     */
    private FileException protocolFailure(String operation, FTPClient client) {
        IOException cause = new IOException(operation + "，replyCode=" + client.getReplyCode()
                + "，reply=" + String.valueOf(client.getReplyString()).trim());
        return FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, cause);
    }

    /**
     * 删除失败写入留下的远程临时文件。
     *
     * @param client FTP 客户端
     * @param temporaryPath 临时文件路径
     */
    private void deleteRemoteTemporary(FTPClient client, String temporaryPath) {
        try {
            if (client.isConnected() && !client.deleteFile(temporaryPath)) {
                log.warn("FTP 临时文件清理未成功，replyCode={}", client.getReplyCode());
            }
        } catch (IOException exception) {
            log.warn("FTP 临时文件清理发生异常", exception);
        }
    }

    /**
     * 注销并断开客户端，清理失败只记录诊断信息。
     *
     * @param client FTP 客户端
     */
    private void disconnect(FTPClient client) {
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            client.logout();
        } catch (IOException exception) {
            log.warn("FTP 注销连接失败", exception);
        }
        try {
            client.disconnect();
        } catch (IOException exception) {
            log.warn("FTP 断开连接失败", exception);
        }
    }

    /**
     * 创建默认 FTP 或 FTPS 客户端。
     *
     * @param storageType 存储类型
     * @param properties FTP 配置
     * @return FTP 客户端
     */
    private static FTPClient createDefaultClient(String storageType, FileProperties.Ftp properties) {
        if ("ftps".equalsIgnoreCase(storageType)) {
            FTPSClient client = new FTPSClient(properties.getProtocol(), properties.isImplicit());
            client.setEndpointCheckingEnabled(properties.isEndpointCheckingEnabled());
            return client;
        }
        return new FTPClient();
    }

    /**
     * 校验 FTP 必填配置和范围。
     *
     * @param properties FTP 配置
     */
    private static void validateProperties(FileProperties.Ftp properties) {
        requireText(properties.getHost(), "ftp.host");
        requireText(properties.getUsername(), "ftp.username");
        requireText(properties.getPassword(), "ftp.password");
        requireText(properties.getCharset(), "ftp.charset");
        if (properties.getPort() < 1 || properties.getPort() > 65_535) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "ftp.port");
        }
        requirePositive(properties.getConnectTimeout(), "ftp.connect-timeout");
        requirePositive(properties.getDefaultTimeout(), "ftp.default-timeout");
        requirePositive(properties.getDataTimeout(), "ftp.data-timeout");
        requirePositive(properties.getKeepAliveInterval(), "ftp.keep-alive-interval");
        try {
            Charset.forName(properties.getCharset());
        } catch (RuntimeException exception) {
            throw FileException.causedBy(FileErrorCode.CONFIGURATION_INVALID, exception, "ftp.charset");
        }
    }

    /**
     * 规范化 FTP 远程根目录。
     *
     * @param configuredPath 配置路径
     * @return 规范化绝对路径
     */
    private static String normalizeBasePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank() || "/".equals(configuredPath.trim())) {
            return "/";
        }
        String candidate = configuredPath.trim().replace('\\', '/');
        if (!candidate.startsWith("/")) {
            candidate = "/" + candidate;
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        for (String segment : candidate.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "ftp.base-path");
            }
        }
        return candidate;
    }

    /**
     * 校验非空文本配置。
     *
     * @param value 配置值
     * @param field 安全字段名
     */
    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, field);
        }
    }

    /**
     * 校验正数时间配置。
     *
     * @param duration 时间值
     * @param field 安全字段名
     */
    private static void requirePositive(java.time.Duration duration, String field) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, field);
        }
    }

    /**
     * 将时间安全转换为 Commons Net 使用的毫秒整数。
     *
     * @param duration 时间值
     * @param field 安全字段名
     * @return 毫秒整数
     */
    private static int toIntMilliseconds(java.time.Duration duration, String field) {
        requirePositive(duration, field);
        long milliseconds = duration.toMillis();
        if (milliseconds > Integer.MAX_VALUE) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, field);
        }
        return (int) milliseconds;
    }

    /**
     * 统计实际读取字节并计算 SHA-256 的输入流包装器。
     */
    private static final class CountingDigestInputStream extends FilterInputStream {
        private final MessageDigest digest;
        private long count;

        /**
         * 创建统计摘要输入流。
         *
         * @param inputStream 上游输入流
         */
        private CountingDigestInputStream(InputStream inputStream) {
            super(inputStream);
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw FileException.causedBy(FileErrorCode.STORAGE_OPERATION_FAILED, exception);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                digest.update((byte) value);
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                digest.update(buffer, offset, read);
                count += read;
            }
            return read;
        }

        /** @return 已读取字节数 */
        private long count() { return count; }

        /** @return SHA-256 十六进制摘要 */
        private String sha256() { return HexFormat.of().formatHex(digest.digest()); }
    }

    /**
     * 关闭时完成 FTP 挂起命令并释放连接的下载流。
     */
    private static final class CompletingFtpInputStream extends FilterInputStream {
        private final FTPClient client;
        private final boolean partialTransfer;
        private boolean closed;

        /**
         * 创建与 FTP 客户端绑定的输入流。
         *
         * @param inputStream FTP 数据流
         * @param client FTP 客户端
         * @param partialTransfer 是否为主动截断的区间传输
         */
        private CompletingFtpInputStream(
                InputStream inputStream,
                FTPClient client,
                boolean partialTransfer) {
            super(inputStream);
            this.client = client;
            this.partialTransfer = partialTransfer;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            try {
                super.close();
            } catch (IOException exception) {
                failure = exception;
            }
            try {
                if (!client.completePendingCommand() && !partialTransfer) {
                    IOException exception = new IOException(
                            "FTP 下载完成命令失败，replyCode=" + client.getReplyCode());
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            } catch (IOException exception) {
                if (partialTransfer) {
                    log.debug("FTP 区间传输主动关闭后的完成命令未成功", exception);
                } else if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            try {
                if (client.isConnected()) {
                    client.logout();
                    client.disconnect();
                }
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
