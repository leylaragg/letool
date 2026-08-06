package com.github.leyland.letool.file.model;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件元数据和一次性输入流的组合资源。
 *
 * <p>调用方必须关闭资源。对于 FTP 等远程存储，关闭动作同时负责完成挂起命令并释放连接。</p>
 */
public final class FileResource implements Closeable {

    private final FileMetadata metadata;
    private final InputStream inputStream;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建可关闭文件资源。
     *
     * @param metadata 文件元数据
     * @param inputStream 一次性文件输入流
     */
    public FileResource(FileMetadata metadata, InputStream inputStream) {
        this.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream 不能为空");
    }

    /**
     * 获取文件元数据。
     *
     * @return 文件元数据
     */
    public FileMetadata metadata() {
        return metadata;
    }

    /**
     * 获取一次性文件输入流。
     *
     * @return 文件输入流
     */
    public InputStream inputStream() {
        return inputStream;
    }

    /**
     * 幂等关闭文件输入流及其关联资源。
     *
     * @throws IOException 资源释放失败时抛出
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            inputStream.close();
        }
    }
}
