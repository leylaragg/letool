package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.model.FileMetadata;
import com.github.leyland.letool.file.model.FileResource;
import com.github.leyland.letool.file.model.StorageCapability;
import com.github.leyland.letool.file.model.StoreRequest;
import com.github.leyland.letool.file.model.StoredFile;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 不依赖 Spring MVC、Servlet 和具体协议 SDK 的文件存储扩展接口。
 *
 * <p>实现类只处理逻辑存储键和字节流。输入流由调用方关闭，返回的
 * {@link FileResource} 由调用方关闭。</p>
 */
public interface FileStorageProvider {

    /**
     * 将文件流写入存储。
     *
     * @param request 写入请求
     * @param inputStream 文件内容输入流；实现类不得主动关闭
     * @return 实际存储结果
     */
    StoredFile store(StoreRequest request, InputStream inputStream);

    /**
     * 打开文件读取资源。
     *
     * @param key 文件逻辑键
     * @return 必须由调用方关闭的文件资源
     */
    FileResource open(String key);

    /**
     * 删除文件。
     *
     * @param key 文件逻辑键
     * @return {@code true} 表示成功删除，{@code false} 表示文件不存在
     */
    boolean delete(String key);

    /**
     * 检查文件是否存在。
     *
     * @param key 文件逻辑键
     * @return 文件是否存在
     */
    boolean exists(String key);

    /**
     * 查询文件或目录元数据。
     *
     * @param key 文件或目录逻辑键
     * @return 文件元数据
     */
    FileMetadata stat(String key);

    /**
     * 列出目录的直接子项。
     *
     * @param directory 目录逻辑键；空字符串表示存储根目录
     * @return 不可变语义的元数据列表
     */
    List<FileMetadata> list(String directory);

    /**
     * 返回当前存储实现明确支持的能力。
     *
     * @return 不可修改的能力集合
     */
    Set<StorageCapability> capabilities();
}
