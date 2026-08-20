package io.github.leylaragg.letool.print.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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

    /** 本次请求独占的随机目录。 */
    private final Path requestDirectory;

    /** 工作区内所有活动文件的总容量上限。 */
    private final long maxBytes;

    /** 已经由框架分配且尚未丢弃的路径。 */
    private final Set<Path> allocatedFiles = new LinkedHashSet<>();

    /** 已关闭文件占用的受控容量。 */
    private final Map<Path, Long> registeredFiles = new LinkedHashMap<>();

    /** 仍在写入的文件及其受控输出。 */
    private final Map<Path, WorkspaceOutput> openOutputs = new LinkedHashMap<>();

    /** 下一个框架文件编号。 */
    private int nextFileNumber = 1;

    /** 已写入和已预留的活动字节数。 */
    private long activeBytes;

    /** 关闭后不允许再次分配或写入。 */
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
     * 打开同时受单文件和工作区总量约束的输出流。
     *
     * @param file 此工作区刚分配的路径
     * @param maxFileBytes 当前文件允许的最大字节数
     * @return 关闭后自动登记容量的受控输出流
     * @throws IOException 路径已使用、文件无法创建或容量越界时抛出
     */
    OutputStream openOutput(Path file, long maxFileBytes) throws IOException {
        requireOpen();
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("PDF 临时文件容量上限必须为正数");
        }
        Path controlled = requireAllocated(file);
        if (registeredFiles.containsKey(controlled) || openOutputs.containsKey(controlled)
                || Files.exists(controlled)) {
            throw new IOException("PDF 临时文件不能重复打开");
        }
        OutputStream target = Files.newOutputStream(
                controlled, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        WorkspaceOutput output = new WorkspaceOutput(controlled, target, maxFileBytes);
        openOutputs.put(controlled, output);
        return output;
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
        if (openOutputs.containsKey(controlled)) {
            throw new IOException("PDF 临时文件仍在写入，不能丢弃");
        }
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
        IOException failure = null;
        // 先结束仍在写入的文件，Windows 下才能可靠删除本次请求目录。
        for (WorkspaceOutput output : new ArrayList<>(openOutputs.values())) {
            try {
                output.close();
            } catch (IOException exception) {
                failure = append(failure, exception);
            }
        }
        closed = true;
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

    /**
     * 在真正写文件前预留容量，失败时不会把当前批次写入磁盘。
     *
     * <p>底层 IO 失败后保留已经预留的额度，宁可提前终止本次请求，也不低估临时占用。</p>
     */
    private final class WorkspaceOutput extends OutputStream {

        /** 当前文件路径。 */
        private final Path file;

        /** 实际文件输出流。 */
        private final OutputStream target;

        /** 当前文件自身的容量上限。 */
        private final long maxFileBytes;

        /** 当前文件已经写入或预留的字节数。 */
        private long fileBytes;

        /** 输出关闭后拒绝继续写入。 */
        private boolean outputClosed;

        /** 创建已经登记为打开状态的工作区输出。 */
        private WorkspaceOutput(Path file, OutputStream target, long maxFileBytes) {
            this.file = file;
            this.target = target;
            this.maxFileBytes = maxFileBytes;
        }

        /** 写入一个字节。 */
        @Override
        public void write(int value) throws IOException {
            reserve(1);
            target.write(value);
        }

        /** 完整写入一批字节，越界时不会留下部分批次。 */
        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, source.length);
            reserve(length);
            target.write(source, offset, length);
        }

        /** 刷新当前临时文件。 */
        @Override
        public void flush() throws IOException {
            requireOutputOpen();
            target.flush();
        }

        /** 关闭文件并把最终受控占用转入已登记集合。 */
        @Override
        public void close() throws IOException {
            if (outputClosed) {
                return;
            }
            outputClosed = true;
            IOException failure = null;
            try {
                target.close();
            } catch (IOException exception) {
                failure = exception;
            } finally {
                openOutputs.remove(file);
                registeredFiles.put(file, fileBytes);
            }
            if (failure != null) {
                throw failure;
            }
        }

        /** 在写文件前同时检查单文件和工作区剩余额度。 */
        private void reserve(int length) throws IOException {
            requireOutputOpen();
            if (length > maxFileBytes - fileBytes || length > maxBytes - activeBytes) {
                throw new CapacityExceededException();
            }
            fileBytes += length;
            activeBytes += length;
        }

        /** 关闭后的输出不再接受操作。 */
        private void requireOutputOpen() throws IOException {
            if (outputClosed) {
                throw new IOException("PDF 临时文件输出已经关闭");
            }
        }
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
