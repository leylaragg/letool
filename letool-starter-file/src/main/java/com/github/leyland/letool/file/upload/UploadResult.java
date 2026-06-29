package com.github.leyland.letool.file.upload;

/**
 * 文件上传结果模型，封装上传成功后的文件元数据。
 *
 * <p>该对象由 {@link FileUploadService#upload} 返回，包含原始文件名、
 * 存储系统中的完整路径、文件大小以及访问 URL。所有字段均为不可变字段。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public class UploadResult {

    /** 原始文件名（含扩展名） */
    private final String fileName;

    /** 文件在存储系统中的完整路径 */
    private final String storagePath;

    /** 文件大小（字节） */
    private final long fileSize;

    /** 文件的访问 URL（通常与 storagePath 相同，可配置 CDN 映射） */
    private final String url;

    /**
     * 构造上传结果。
     *
     * @param fileName    原始文件名
     * @param storagePath 存储后的完整路径
     * @param fileSize    文件大小（字节）
     * @param url         文件访问 URL
     */
    public UploadResult(String fileName, String storagePath, long fileSize, String url) {
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.url = url;
    }

    /**
     * 获取原始文件名。
     *
     * @return 原始文件名（含扩展名）
     */
    public String getFileName() { return fileName; }

    /**
     * 获取文件在存储系统中的完整路径。
     *
     * @return 存储路径
     */
    public String getStoragePath() { return storagePath; }

    /**
     * 获取文件大小。
     *
     * @return 文件大小（字节）
     */
    public long getFileSize() { return fileSize; }

    /**
     * 获取文件访问 URL。
     *
     * @return 文件 URL
     */
    public String getUrl() { return url; }
}
