package com.github.leyland.letool.print.pdf;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 隔离单次 PDF 请求中间文件和活动容量的临时工作区。
 *
 * @author leyland
 */
final class PdfRenderWorkspace implements AutoCloseable {

    private final Path requestDirectory;
    private final long maxBytes;
    private final Set<Path> allocatedFiles = new LinkedHashSet<>();
    private final Map<Path, Long> registeredFiles = new LinkedHashMap<>();
    private int nextFileNumber = 1;
    private long activeBytes;
    private boolean closed;

    /**
     * 在宿主指定根目录下创建随机请求目录。
     *
     * @param root 可信临时根目录
     * @param maxBytes 中间文件活动字节上限
     * @return 新请求工作区
     * @throws IOException 根目录或请求目录无法创建时抛出
     */
    static PdfRenderWorkspace open(Path root, long maxBytes) throws IOException {
        Objects.requireNonNull(root, "临时根目录不能为空");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("临时文件容量上限必须为正数");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        Path requestDirectory = Files.createTempDirectory(normalizedRoot, "request-")
                .toAbsolutePath().normalize();
        if (!requestDirectory.getParent().equals(normalizedRoot)) {
            throw new IOException("PDF 临时请求目录不在配置根目录内");
        }
        return new PdfRenderWorkspace(requestDirectory, maxBytes);
    }

    private PdfRenderWorkspace(Path requestDirectory, long maxBytes) {
        this.requestDirectory = requestDirectory;
        this.maxBytes = maxBytes;
    }

    /**
     * 分配由框架编号的中间 PDF 路径。
     *
     * @return 尚未创建内容的安全路径
     */
    Path allocate() {
        requireOpen();
        Path file = requestDirectory.resolve("unit-%04d.pdf".formatted(nextFileNumber++));
        allocatedFiles.add(file);
        return file;
    }

    /**
     * 按文件实际大小登记活动容量。
     *
     * @param file 此工作区分配并已写完的普通文件
     * @throws IOException 文件非法、重复登记或容量越界时抛出
     */
    void register(Path file) throws IOException {
        requireOpen();
        Path controlled = requireAllocated(file);
        if (registeredFiles.containsKey(controlled)) {
            throw new IOException("PDF 临时文件不能重复登记");
        }
        if (!Files.isRegularFile(controlled, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("PDF 临时文件尚未写入");
        }
        long size = Files.size(controlled);
        if (size > maxBytes - activeBytes) {
            throw new CapacityExceededException();
        }
        registeredFiles.put(controlled, size);
        activeBytes += size;
    }

    /**
     * 删除不再使用的轮次文件并释放其活动容量。
     *
     * @param file 此工作区分配的文件
     * @throws IOException 文件删除失败时抛出
     */
    void discard(Path file) throws IOException {
        requireOpen();
        Path controlled = requireAllocated(file);
        Files.deleteIfExists(controlled);
        Long size = registeredFiles.remove(controlled);
        if (size != null) {
            activeBytes -= size;
        }
        allocatedFiles.remove(controlled);
    }

    /** @return 当前已登记且尚未丢弃的活动字节数 */
    long activeBytes() {
        return activeBytes;
    }

    /** 包内测试和渲染管线只读访问本次随机目录。 */
    Path requestDirectory() {
        return requestDirectory;
    }

    /** 只接受当前请求内由 allocate 返回的原始路径。 */
    private Path requireAllocated(Path file) throws IOException {
        if (file == null || !file.toAbsolutePath().normalize().equals(file)
                || !allocatedFiles.contains(file)) {
            throw new IOException("PDF 临时文件不属于当前请求工作区");
        }
        return file;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("PDF 临时工作区已经关闭");
        }
    }

    /**
     * 删除框架分配的文件和本次请求目录，不触碰临时根目录中的其他内容。
     *
     * @throws IOException 任一清理操作失败时抛出
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        for (Path file : allocatedFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                failure = append(failure, exception);
            }
        }
        try {
            Files.deleteIfExists(requestDirectory);
        } catch (IOException exception) {
            failure = append(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** 保留第一次清理失败，其余失败作为补充线索。 */
    private static IOException append(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    /** 中间文件活动容量越界时使用的内部信号。 */
    static final class CapacityExceededException extends IOException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 创建不携带临时路径或业务内容的容量异常。 */
        private CapacityExceededException() {
            super("PDF 临时文件活动容量超过配置上限");
        }
    }
}
