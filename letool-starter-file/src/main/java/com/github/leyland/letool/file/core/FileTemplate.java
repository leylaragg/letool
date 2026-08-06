package com.github.leyland.letool.file.core;

import com.github.leyland.letool.file.compress.ZipLimits;
import com.github.leyland.letool.file.compress.ZipUtil;
import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.exception.FileErrorCode;
import com.github.leyland.letool.file.exception.FileException;
import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.OverwritePolicy;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;
import com.github.leyland.letool.file.range.ByteRange;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import com.github.leyland.letool.file.storage.StorageKey;
import com.github.leyland.letool.file.transfer.InMemoryTransferProgressMonitor;
import com.github.leyland.letool.file.transfer.TransferProgressMonitor;
import com.github.leyland.letool.file.transfer.TransferStatus;
import com.github.leyland.letool.file.transfer.TransferType;
import com.github.leyland.letool.file.util.FileNameUtil;
import com.github.leyland.letool.file.util.MimeTypeUtil;
import com.github.leyland.letool.file.validation.FileTypeDetector;
import com.github.leyland.letool.file.validation.FileValidationContext;
import com.github.leyland.letool.file.validation.FileValidationPolicy;
import com.github.leyland.letool.file.validation.MagicNumberFileTypeDetector;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 面向业务代码的统一文件操作门面。
 *
 * <p>门面负责上传限制、文件名处理、校验扩展、下载响应适配和异常边界，
 * 具体存储协议由 {@link FileStorageProvider} 负责。</p>
 */
public final class FileTemplate {

    private static final Logger log = LoggerFactory.getLogger(FileTemplate.class);
    private static final int HEADER_LIMIT = 8 * 1024;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;

    private final FileStorageProvider storageProvider;
    private final FileProperties properties;
    private final FileTypeDetector fileTypeDetector;
    private final List<FileValidationPolicy> validationPolicies;
    private final TransferProgressMonitor progressMonitor;

    /**
     * 使用默认轻量类型探测器创建文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param properties 文件模块配置
     */
    public FileTemplate(FileStorageProvider storageProvider, FileProperties properties) {
        this(storageProvider, properties, new MagicNumberFileTypeDetector(), List.of(),
                defaultProgressMonitor());
    }

    /**
     * 创建支持用户校验扩展的文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param properties 文件模块配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 有序上传校验策略
     */
    public FileTemplate(
            FileStorageProvider storageProvider,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            List<FileValidationPolicy> validationPolicies) {
        this(storageProvider, properties, fileTypeDetector, validationPolicies,
                defaultProgressMonitor());
    }

    /**
     * 创建支持用户校验扩展和自定义进度监视器的文件门面。
     *
     * @param storageProvider 文件存储提供者
     * @param properties 文件模块配置
     * @param fileTypeDetector 文件类型探测器
     * @param validationPolicies 有序上传校验策略
     * @param progressMonitor 传输进度监视器
     */
    public FileTemplate(
            FileStorageProvider storageProvider,
            FileProperties properties,
            FileTypeDetector fileTypeDetector,
            List<FileValidationPolicy> validationPolicies,
            TransferProgressMonitor progressMonitor) {
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.fileTypeDetector = Objects.requireNonNull(fileTypeDetector, "fileTypeDetector 不能为空");
        this.validationPolicies = validationPolicies == null ? List.of() : List.copyOf(validationPolicies);
        this.progressMonitor = Objects.requireNonNull(progressMonitor, "progressMonitor 不能为空");
        validateUploadConfiguration(properties.getUpload());
        validateArchiveConfiguration(properties.getArchive());
    }

    /**
     * 创建直接实例化门面时使用的有界默认进度监视器。
     *
     * @return 默认进度监视器
     */
    private static TransferProgressMonitor defaultProgressMonitor() {
        return new InMemoryTransferProgressMonitor(
                Duration.ofMinutes(30),
                10_000,
                Duration.ofMillis(200),
                64 * 1024,
                List.of());
    }

    /**
     * 生成可由前端或业务层公开传递的传输编号。
     *
     * @return 新传输编号
     */
    public String generateTransferId() {
        return progressMonitor.generateTransferId();
    }

    /**
     * 上传 Spring MVC 文件。
     *
     * @param file Multipart 文件
     * @param directory 目标逻辑目录
     * @return 存储结果
     */
    public StoredFile upload(MultipartFile file, String directory) {
        return upload(file, directory, generateTransferId());
    }

    /**
     * 使用指定传输编号上传 Spring MVC 文件。
     *
     * @param file Multipart 文件
     * @param directory 目标逻辑目录
     * @param transferId 传输编号
     * @return 存储结果
     */
    public StoredFile upload(MultipartFile file, String directory, String transferId) {
        if (file == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "file");
        }
        try (InputStream inputStream = file.getInputStream()) {
            return upload(
                    inputStream,
                    file.getSize(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    directory,
                    transferId);
        } catch (FileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.TRANSFER_FAILED, exception);
        }
    }

    /**
     * 上传普通输入流。
     *
     * @param inputStream 文件输入流；方法不会关闭该流
     * @param declaredSize 声明文件大小；未知时传 {@code -1}
     * @param originalName 原始文件名
     * @param contentType 声明媒体类型
     * @param directory 目标逻辑目录
     * @return 存储结果
     */
    public StoredFile upload(
            InputStream inputStream,
            long declaredSize,
            String originalName,
            String contentType,
            String directory) {
        return upload(
                inputStream,
                declaredSize,
                originalName,
                contentType,
                directory,
                generateTransferId());
    }

    /**
     * 使用指定传输编号上传普通输入流。
     *
     * @param inputStream 文件输入流；方法不会关闭该流
     * @param declaredSize 声明文件大小；未知时传 {@code -1}
     * @param originalName 原始文件名
     * @param contentType 声明媒体类型
     * @param directory 目标逻辑目录
     * @param transferId 传输编号
     * @return 存储结果
     */
    public StoredFile upload(
            InputStream inputStream,
            long declaredSize,
            String originalName,
            String contentType,
            String directory,
            String transferId) {
        if (inputStream == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "inputStream");
        }
        progressMonitor.begin(transferId, TransferType.UPLOAD, declaredSize, 0);
        try {
            StoredFile storedFile = doUpload(
                    inputStream,
                    declaredSize,
                    originalName,
                    contentType,
                    directory,
                    transferId);
            progressMonitor.update(transferId, storedFile.size());
            progressMonitor.transition(transferId, TransferStatus.COMPLETED, null);
            return storedFile;
        } catch (RuntimeException exception) {
            transitionFailed(transferId, exception);
            throw exception;
        }
    }

    /**
     * 执行上传校验并写入底层存储。
     *
     * @param inputStream 文件输入流
     * @param declaredSize 声明文件大小
     * @param originalName 原始文件名
     * @param contentType 声明媒体类型
     * @param directory 目标逻辑目录
     * @param transferId 传输编号
     * @return 存储结果
     */
    private StoredFile doUpload(
            InputStream inputStream,
            long declaredSize,
            String originalName,
            String contentType,
            String directory,
            String transferId) {
        FileProperties.Upload uploadProperties = properties.getUpload();
        long maximumBytes = uploadProperties.getMaxSize().toBytes();
        if (declaredSize > maximumBytes) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件大小超过限制");
        }
        if (declaredSize == 0 && !uploadProperties.isAllowEmpty()) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "不允许上传空文件");
        }

        String clientName = FileNameUtil.extractClientFileName(originalName);
        String safeName = FileNameUtil.sanitize(clientName);
        if (safeName == null || safeName.isBlank()) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件名为空");
        }
        String extension = FileNameUtil.getExtension(safeName);
        validateExtension(extension, uploadProperties.getAllowedExtensions());
        String normalizedContentType = normalizeContentType(contentType, safeName);
        validateContentType(normalizedContentType, uploadProperties.getAllowedContentTypes());

        BufferedInputStream bufferedInputStream = inputStream instanceof BufferedInputStream buffered
                ? buffered
                : new BufferedInputStream(inputStream, HEADER_LIMIT);
        byte[] header = readHeader(bufferedInputStream);
        String detectedType = fileTypeDetector.detect(
                header.clone(), safeName, normalizedContentType);
        FileValidationContext context = new FileValidationContext(
                clientName,
                safeName,
                extension,
                normalizedContentType,
                declaredSize,
                detectedType == null ? "UNKNOWN" : detectedType,
                header);
        for (FileValidationPolicy validationPolicy : validationPolicies) {
            validationPolicy.validate(context);
        }

        String storedName = FileNameUtil.generateUniqueName(safeName);
        String key = StorageKey.join(directory, storedName);
        LimitedInputStream limitedInputStream = new LimitedInputStream(bufferedInputStream, maximumBytes);
        ProgressInputStream progressInputStream = new ProgressInputStream(
                limitedInputStream, progressMonitor, transferId, declaredSize);
        StoredFile storedFile = storageProvider.store(
                new StoreRequest(
                        key,
                        declaredSize,
                        clientName,
                        normalizedContentType,
                        OverwritePolicy.FAIL),
                progressInputStream);
        if (storedFile.size() == 0 && !uploadProperties.isAllowEmpty()) {
            storageProvider.delete(storedFile.key());
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "不允许上传空文件");
        }
        return storedFile;
    }

    /**
     * 打开文件资源。
     *
     * @param key 文件逻辑键
     * @return 可关闭文件资源
     */
    public FileResource open(String key) {
        return storageProvider.open(key);
    }

    /**
     * 将文件流式写入 Servlet 响应。
     *
     * @param key 文件逻辑键
     * @param displayName 浏览器显示文件名
     * @param response Servlet 响应
     */
    public void download(String key, String displayName, HttpServletResponse response) {
        download(key, displayName, response, generateTransferId());
    }

    /**
     * 使用指定传输编号将文件流式写入 Servlet 响应。
     *
     * @param key 文件逻辑键
     * @param displayName 浏览器显示文件名
     * @param response Servlet 响应
     * @param transferId 传输编号
     */
    public void download(
            String key,
            String displayName,
            HttpServletResponse response,
            String transferId) {
        if (response == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "response");
        }
        String safeDisplayName = requireDisplayName(displayName);
        boolean begun = false;
        try (FileResource resource = storageProvider.open(key)) {
            FileMetadata metadata = resource.metadata();
            progressMonitor.begin(transferId, TransferType.DOWNLOAD, metadata.size(), 0);
            begun = true;
            prepareDownloadHeaders(response, metadata, safeDisplayName, metadata.size());
            if (storageProvider.capabilities().contains(StorageCapability.RANGE_READ)) {
                response.setHeader("Accept-Ranges", "bytes");
            }
            OutputStream outputStream = response.getOutputStream();
            copy(resource.inputStream(), outputStream, transferId, 0);
            outputStream.flush();
            progressMonitor.transition(transferId, TransferStatus.COMPLETED, null);
        } catch (FileException exception) {
            if (begun) {
                transitionFailed(transferId, exception);
            }
            throw exception;
        } catch (IOException exception) {
            FileException fileException = FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
            if (begun) {
                transitionFailed(transferId, fileException);
            }
            throw fileException;
        }
    }

    /**
     * 按 HTTP 单区间语义下载文件；没有 Range 请求头时回退到完整下载。
     *
     * @param key 文件逻辑键
     * @param displayName 浏览器显示文件名
     * @param rangeHeader HTTP Range 请求头
     * @param response Servlet 响应
     */
    public void downloadRange(
            String key,
            String displayName,
            String rangeHeader,
            HttpServletResponse response) {
        downloadRange(key, displayName, rangeHeader, response, generateTransferId());
    }

    /**
     * 使用指定传输编号按 HTTP 单区间语义下载文件。
     *
     * @param key 文件逻辑键
     * @param displayName 浏览器显示文件名
     * @param rangeHeader HTTP Range 请求头
     * @param response Servlet 响应
     * @param transferId 传输编号
     */
    public void downloadRange(
            String key,
            String displayName,
            String rangeHeader,
            HttpServletResponse response,
            String transferId) {
        if (response == null) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "response");
        }
        if (rangeHeader == null || rangeHeader.isBlank()) {
            download(key, displayName, response, transferId);
            return;
        }
        String safeDisplayName = requireDisplayName(displayName);
        FileMetadata metadata = storageProvider.stat(key);
        ByteRange range;
        try {
            range = ByteRange.parse(rangeHeader, metadata.size());
        } catch (IllegalArgumentException exception) {
            prepareRangeNotSatisfiable(response, metadata.size());
            return;
        }
        if (!storageProvider.capabilities().contains(StorageCapability.RANGE_READ)) {
            throw FileException.of(FileErrorCode.CAPABILITY_UNSUPPORTED, "RANGE_READ");
        }

        boolean begun = false;
        try (FileResource resource = storageProvider.openRange(
                key, range.start(), range.length())) {
            progressMonitor.begin(
                    transferId, TransferType.RANGE_DOWNLOAD, range.length(), 0);
            begun = true;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader(
                    "Content-Range",
                    "bytes " + range.start() + "-" + range.end() + "/" + metadata.size());
            prepareDownloadHeaders(
                    response, metadata, safeDisplayName, range.length());
            OutputStream outputStream = response.getOutputStream();
            copy(resource.inputStream(), outputStream, transferId, 0);
            outputStream.flush();
            progressMonitor.transition(transferId, TransferStatus.COMPLETED, null);
        } catch (FileException exception) {
            if (begun) {
                transitionFailed(transferId, exception);
            }
            throw exception;
        } catch (IOException exception) {
            FileException fileException = FileException.causedBy(
                    FileErrorCode.TRANSFER_FAILED, exception);
            if (begun) {
                transitionFailed(transferId, fileException);
            }
            throw fileException;
        }
    }

    /**
     * 删除文件。
     *
     * @param key 文件逻辑键
     * @return 是否成功删除
     */
    public boolean delete(String key) {
        return storageProvider.delete(key);
    }

    /**
     * 检查文件是否存在。
     *
     * @param key 文件逻辑键
     * @return 文件是否存在
     */
    public boolean exists(String key) {
        return storageProvider.exists(key);
    }

    /**
     * 查询文件或目录元数据。
     *
     * @param key 文件逻辑键
     * @return 文件元数据
     */
    public FileMetadata stat(String key) {
        return storageProvider.stat(key);
    }

    /**
     * 列出目录的直接子项。
     *
     * @param directory 目录逻辑键
     * @return 目录直接子项
     */
    public List<FileMetadata> list(String directory) {
        return storageProvider.list(directory);
    }

    /** @return 当前存储提供者声明的能力 */
    public Set<StorageCapability> capabilities() { return storageProvider.capabilities(); }

    /**
     * 压缩文件或目录，并通过临时文件避免输出半成品。
     *
     * @param sourcePath 源文件或目录
     * @param outputZip 输出 ZIP 文件
     * @param includeRoot 是否保留源目录名称
     */
    public void compress(Path sourcePath, Path outputZip, boolean includeRoot) {
        ZipUtil.compress(sourcePath, outputZip, includeRoot);
    }

    /**
     * 按文件模块配置的安全上限解压 ZIP 文件。
     *
     * @param inputZip ZIP 文件
     * @param targetDirectory 目标目录
     */
    public void decompress(Path inputZip, Path targetDirectory) {
        FileProperties.Archive archive = properties.getArchive();
        ZipUtil.decompress(
                inputZip,
                targetDirectory,
                new ZipLimits(
                        archive.getMaxEntries(),
                        archive.getMaxEntrySize().toBytes(),
                        archive.getMaxTotalSize().toBytes()));
    }

    /**
     * 读取有限文件头并恢复流位置。
     *
     * @param inputStream 支持标记的缓冲输入流
     * @return 实际文件头字节
     */
    private byte[] readHeader(BufferedInputStream inputStream) {
        inputStream.mark(HEADER_LIMIT + 1);
        try {
            byte[] header = inputStream.readNBytes(HEADER_LIMIT);
            inputStream.reset();
            return header;
        } catch (IOException exception) {
            throw FileException.causedBy(FileErrorCode.TRANSFER_FAILED, exception);
        }
    }

    /**
     * 校验扩展名白名单。
     *
     * @param extension 文件扩展名
     * @param allowedExtensions 允许列表
     */
    private void validateExtension(String extension, List<String> allowedExtensions) {
        Set<String> normalized = normalizedSet(allowedExtensions, true);
        if (!normalized.isEmpty() && !normalized.contains(extension.toLowerCase(Locale.ROOT))) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件扩展名不允许");
        }
    }

    /**
     * 校验声明媒体类型白名单。
     *
     * @param contentType 声明媒体类型
     * @param allowedContentTypes 允许列表
     */
    private void validateContentType(String contentType, List<String> allowedContentTypes) {
        Set<String> normalized = normalizedSet(allowedContentTypes, false);
        if (!normalized.isEmpty() && !normalized.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件媒体类型不允许");
        }
    }

    /**
     * 规范化配置白名单。
     *
     * @param values 原始列表
     * @param extension 是否为扩展名列表
     * @return 小写不可变语义集合
     */
    private Set<String> normalizedSet(List<String> values, boolean extension) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            if (extension && candidate.startsWith(".")) {
                candidate = candidate.substring(1);
            }
            normalized.add(candidate);
        }
        return Set.copyOf(normalized);
    }

    /**
     * 规范化声明媒体类型。
     *
     * @param contentType 声明媒体类型
     * @param fileName 安全文件名
     * @return 非空媒体类型
     */
    private String normalizeContentType(String contentType, String fileName) {
        if (contentType == null || contentType.isBlank()) {
            return MimeTypeUtil.getMimeType(fileName);
        }
        int parameterIndex = contentType.indexOf(';');
        String normalized = parameterIndex < 0
                ? contentType.trim()
                : contentType.substring(0, parameterIndex).trim();
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 校验上传配置。
     *
     * @param uploadProperties 上传配置
     */
    private void validateUploadConfiguration(FileProperties.Upload uploadProperties) {
        if (uploadProperties.getMaxSize() == null || uploadProperties.getMaxSize().toBytes() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "upload.max-size");
        }
    }

    /**
     * 校验 ZIP 解压安全配置，避免配置存在但运行时不生效。
     *
     * @param archiveProperties 归档配置
     */
    private void validateArchiveConfiguration(FileProperties.Archive archiveProperties) {
        if (archiveProperties.getMaxEntries() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "archive.max-entries");
        }
        if (archiveProperties.getMaxEntrySize() == null
                || archiveProperties.getMaxEntrySize().toBytes() <= 0) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "archive.max-entry-size");
        }
        if (archiveProperties.getMaxTotalSize() == null
                || archiveProperties.getMaxTotalSize().toBytes()
                < archiveProperties.getMaxEntrySize().toBytes()) {
            throw FileException.of(FileErrorCode.CONFIGURATION_INVALID, "archive.max-total-size");
        }
    }

    /**
     * 校验并规范化浏览器显示文件名。
     *
     * @param displayName 浏览器显示文件名
     * @return 安全文件名
     */
    private String requireDisplayName(String displayName) {
        String safeDisplayName = FileNameUtil.sanitize(
                FileNameUtil.extractClientFileName(displayName));
        if (safeDisplayName == null || safeDisplayName.isBlank()) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "displayName");
        }
        return safeDisplayName;
    }

    /**
     * 写入完整下载和区间下载共用的响应头。
     *
     * @param response Servlet 响应
     * @param metadata 文件元数据
     * @param displayName 安全显示文件名
     * @param contentLength 本次响应体长度
     */
    private void prepareDownloadHeaders(
            HttpServletResponse response,
            FileMetadata metadata,
            String displayName,
            long contentLength) {
        response.setContentType(metadata.contentType());
        response.setContentLengthLong(contentLength);
        response.setHeader(
                "Content-Disposition",
                ContentDisposition.attachment()
                        .filename(displayName, StandardCharsets.UTF_8)
                        .build()
                        .toString());
    }

    /**
     * 写入 HTTP 416 响应，向客户端返回完整资源大小以便重新协商区间。
     *
     * @param response Servlet 响应
     * @param resourceLength 完整资源长度
     */
    private void prepareRangeNotSatisfiable(
            HttpServletResponse response,
            long resourceLength) {
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Range", "bytes */" + resourceLength);
        response.setContentLengthLong(0);
    }

    /**
     * 流式复制文件内容，并在输出成功后更新可信进度。
     *
     * @param inputStream 文件输入流
     * @param outputStream Servlet 输出流
     * @param transferId 传输编号
     * @param initialBytes 初始已确认字节数
     * @throws IOException 传输失败时抛出
     */
    private void copy(
            InputStream inputStream,
            OutputStream outputStream,
            String transferId,
            long initialBytes) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long transferredBytes = initialBytes;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
            transferredBytes += read;
            progressMonitor.update(transferId, transferredBytes);
        }
    }

    /**
     * 在不覆盖原始异常的前提下把进度转换为失败状态。
     *
     * @param transferId 传输编号
     * @param failure 原始失败
     */
    private void transitionFailed(String transferId, RuntimeException failure) {
        try {
            String safeReason = failure instanceof FileException fileException
                    ? fileException.getCode()
                    : "传输失败";
            progressMonitor.transition(transferId, TransferStatus.FAILED, safeReason);
        } catch (RuntimeException transitionException) {
            log.debug("更新传输失败状态时发生并发冲突，transferId={}",
                    transferId, transitionException);
        }
    }

    /**
     * 在底层存储实际读取上传内容时更新进度，避免预读文件头造成重复计数。
     */
    private static final class ProgressInputStream extends FilterInputStream {
        private final TransferProgressMonitor progressMonitor;
        private final String transferId;
        private final long totalBytes;
        private long transferredBytes;

        /**
         * 创建上传进度输入流。
         *
         * @param inputStream 上游输入流
         * @param progressMonitor 进度监视器
         * @param transferId 传输编号
         * @param totalBytes 声明总字节数；未知时为 {@code -1}
         */
        private ProgressInputStream(
                InputStream inputStream,
                TransferProgressMonitor progressMonitor,
                String transferId,
                long totalBytes) {
            super(inputStream);
            this.progressMonitor = progressMonitor;
            this.transferId = transferId;
            this.totalBytes = totalBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                updateProgress(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                updateProgress(read);
            }
            return read;
        }

        /**
         * 单调更新实际读取字节数；声明大小被伪造时由上传限制负责拒绝超额内容。
         *
         * @param increment 本次读取字节数
         */
        private void updateProgress(int increment) {
            transferredBytes += increment;
            long reliableBytes = totalBytes < 0
                    ? transferredBytes
                    : Math.min(transferredBytes, totalBytes);
            progressMonitor.update(transferId, reliableBytes);
        }
    }

    /**
     * 在存储读取过程中强制执行实际字节上限。
     */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long count;

        /**
         * 创建限流输入流。
         *
         * @param inputStream 上游输入流
         * @param maximumBytes 最大允许读取字节数
         */
        private LimitedInputStream(InputStream inputStream, long maximumBytes) {
            super(inputStream);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0 && ++count > maximumBytes) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件大小超过限制");
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            long remainingWithProbe = maximumBytes - count + 1;
            int allowedLength = (int) Math.min(length, Math.max(1, remainingWithProbe));
            int read = super.read(buffer, offset, allowedLength);
            if (read > 0 && (count += read) > maximumBytes) {
                throw FileException.of(FileErrorCode.UPLOAD_REJECTED, "文件大小超过限制");
            }
            return read;
        }
    }
}
