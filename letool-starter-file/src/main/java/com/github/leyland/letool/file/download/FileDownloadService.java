package com.github.leyland.letool.file.download;

import com.github.leyland.letool.file.storage.FileStorageProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载服务，负责将存储后端中的文件流式写入 HTTP 响应。
 *
 * <p>使用 8KB 缓冲区逐块传输，避免大文件撑爆内存。支持设置下载文件名
 * （通过 Content-Disposition 头），并自动处理中文文件名的 URL 编码。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * @GetMapping("/download")
 * public void download(@RequestParam String path, HttpServletResponse response) {
 *     fileDownloadService.download(path, "report.pdf", response);
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class FileDownloadService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);

    /** 文件存储后端 */
    private final FileStorageProvider storageProvider;

    /**
     * 构造下载服务。
     *
     * @param storageProvider 文件存储提供者
     */
    public FileDownloadService(FileStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    // ===== HTTP 响应下载 =====

    /**
     * 从存储后端下载文件并直接写入 HTTP 响应输出流。
     *
     * <p>响应设置：</p>
     * <ul>
     *   <li>{@code Content-Type: application/octet-stream}</li>
     *   <li>{@code Content-Disposition: attachment} — 触发浏览器下载</li>
     *   <li>{@code Content-Length} — 告知浏览器文件大小</li>
     * </ul>
     *
     * @param path        文件在存储系统中的路径
     * @param displayName 下载时显示的文件名（支持中文）
     * @param response    HTTP 响应对象
     * @throws RuntimeException 读取或写入过程发生 I/O 错误时抛出
     */
    public void download(String path, String displayName, HttpServletResponse response) {
        try (InputStream is = storageProvider.download(path);
             OutputStream os = response.getOutputStream()) {

            // 设置响应头，触发浏览器下载
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(displayName, StandardCharsets.UTF_8) + "\"");
            response.setHeader("Content-Length", String.valueOf(is.available()));

            // 8KB 缓冲区流式传输
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            log.debug("File downloaded: {} -> {}", path, displayName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + path, e);
        }
    }

    // ===== 直接获取输入流 =====

    /**
     * 获取文件内容的输入流，不做 HTTP 响应处理。
     *
     * <p>适用于需要在下载前进行额外处理（如加解密、压缩）的场景。
     * 调用方负责关闭返回的输入流。</p>
     *
     * @param path 文件在存储系统中的路径
     * @return 文件内容的输入流
     */
    public InputStream getInputStream(String path) {
        return storageProvider.download(path);
    }
}
