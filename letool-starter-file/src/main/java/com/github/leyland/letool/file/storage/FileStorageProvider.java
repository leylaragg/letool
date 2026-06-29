package com.github.leyland.letool.file.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 文件存储提供者接口，定义文件存储的标准契约。
 *
 * <p>所有文件存储实现（本地、FTP、SFTP、OSS 等）都应实现此接口，
 * 以提供统一的文件上传、下载、删除、存在性检查、列表查询能力。
 * 上层服务（如 {@code FileUploadService}、{@code FileDownloadService}）
 * 依赖此接口而非具体实现，从而支持通过配置切换存储后端。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public interface FileStorageProvider {

    // ===== 核心操作 =====

    /**
     * 上传文件到存储系统。
     *
     * @param inputStream 文件内容的输入流
     * @param path        目标存储路径（相对于存储根目录）
     * @param fileName    目标文件名
     * @return 文件在存储系统中的完整路径标识
     * @throws java.io.UncheckedIOException 上传过程中发生 I/O 错误时抛出
     */
    String upload(InputStream inputStream, String path, String fileName);

    /**
     * 从存储系统中下载文件。
     *
     * @param path 文件在存储系统中的完整路径
     * @return 文件内容的输入流，调用方负责关闭
     * @throws java.io.UncheckedIOException 文件不存在或读取失败时抛出
     */
    InputStream download(String path);

    /**
     * 删除存储系统中的指定文件。
     *
     * @param path 文件在存储系统中的完整路径
     * @return {@code true} 删除成功，{@code false} 文件不存在
     * @throws java.io.UncheckedIOException 删除过程中发生 I/O 错误时抛出
     */
    boolean delete(String path);

    /**
     * 检查文件是否存在于存储系统中。
     *
     * @param path 文件在存储系统中的完整路径
     * @return {@code true} 文件存在，{@code false} 不存在
     */
    boolean exists(String path);

    /**
     * 列出指定路径下的所有文件和目录。
     *
     * @param path 目录路径（相对于存储根目录）
     * @return 文件/目录信息列表，若目录为空或不存在则返回空列表
     * @throws java.io.UncheckedIOException 列表读取过程中发生 I/O 错误时抛出
     */
    List<FileInfo> list(String path);

    // ===== 文件信息模型 =====

    /**
     * 文件/目录的元数据信息，由存储提供者在 {@link #list(String)} 时返回。
     *
     * <p>该对象为不可变对象，所有字段通过构造函数传入且仅提供 getter。</p>
     */
    class FileInfo {

        /** 文件或目录的名称 */
        private final String name;

        /** 文件或目录的完整存储路径 */
        private final String path;

        /** 文件大小（字节），目录时为 0 */
        private final long size;

        /** 是否为目录 */
        private final boolean directory;

        /** 最后修改时间的时间戳（毫秒） */
        private final long lastModified;

        /**
         * 构造一个文件信息对象。
         *
         * @param name         文件或目录的名称
         * @param path         完整存储路径
         * @param size         文件大小（字节）
         * @param directory    是否为目录
         * @param lastModified 最后修改时间（毫秒时间戳）
         */
        public FileInfo(String name, String path, long size, boolean directory, long lastModified) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.directory = directory;
            this.lastModified = lastModified;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public long getSize() { return size; }
        public boolean isDirectory() { return directory; }
        public long getLastModified() { return lastModified; }
    }
}
