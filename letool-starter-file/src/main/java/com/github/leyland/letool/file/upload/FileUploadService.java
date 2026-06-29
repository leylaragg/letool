package com.github.leyland.letool.file.upload;

import com.github.leyland.letool.file.config.FileProperties;
import com.github.leyland.letool.file.storage.FileStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 文件上传服务，封装 Spring MVC {@link MultipartFile} 到存储后端的完整上传流程。
 *
 * <p><b>上传流程：</b></p>
 * <ol>
 *   <li><b>验证</b> — 检查文件是否为空、扩展名是否在允许列表中</li>
 *   <li><b>生成存储名</b> — 用 UUID 生成唯一文件名，保留原扩展名</li>
 *   <li><b>委托存储</b> — 调用 {@link FileStorageProvider#upload} 将文件写入具体存储后端</li>
 *   <li><b>返回结果</b> — 封装 {@link UploadResult} 包含原始文件名、存储路径、大小等</li>
 * </ol>
 *
 * <p>通常在 Controller 层注入使用：</p>
 * <pre>{@code
 * @Autowired
 * private FileUploadService fileUploadService;
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    /** 文件存储后端 */
    private final FileStorageProvider storageProvider;

    /** 文件模块配置属性 */
    private final FileProperties properties;

    /**
     * 构造上传服务。
     *
     * @param storageProvider 文件存储提供者
     * @param properties      文件模块配置属性
     */
    public FileUploadService(FileStorageProvider storageProvider, FileProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    /**
     * 上传单个文件。
     *
     * <p>文件会以 UUID 重命名后写入存储后端，保留原始文件扩展名。</p>
     *
     * @param file Spring MVC 的 MultipartFile 对象
     * @param path 目标存储子目录（相对于存储根目录）
     * @return 包含原始文件名、存储路径、文件大小的上传结果
     * @throws IllegalArgumentException 文件为空或类型不允许时抛出
     * @throws RuntimeException         读取文件流失败时抛出
     */
    public UploadResult upload(MultipartFile file, String path) {
        validate(file);

        // 提取扩展名
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";

        // 生成 UUID 文件名，保留原始扩展名
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;

        try (InputStream is = file.getInputStream()) {
            String storagePath = storageProvider.upload(is, path, storedName);
            log.info("File uploaded: {} -> {}", originalName, storagePath);
            return new UploadResult(originalName, storagePath, file.getSize(), storagePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file: " + originalName, e);
        }
    }

    /**
     * 验证上传文件的有效性。
     *
     * <p>检查规则：</p>
     * <ul>
     *   <li>文件不能为空</li>
     *   <li>若配置了 allowedTypes，文件扩展名必须在白名单内</li>
     * </ul>
     *
     * @param file 待验证的 MultipartFile
     * @throws IllegalArgumentException 验证不通过时抛出
     */
    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String[] allowedTypes = properties.getUpload().getAllowedTypes();
        if (allowedTypes != null && allowedTypes.length > 0) {
            String originalName = file.getOriginalFilename();
            if (originalName != null) {
                String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
                boolean allowed = false;
                for (String allowedType : allowedTypes) {
                    if (allowedType.equalsIgnoreCase(ext)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new IllegalArgumentException("File type not allowed: " + ext);
                }
            }
        }
    }
}
