package io.github.leylaragg.letool.print.pdf;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 为一次 PDF 组装共享受限的文件缓存。
 *
 * <p>每个 PDFBox 文档拿到独立视图，关闭文档时只结束自己的视图；底层缓存由组装请求统一关闭。</p>
 *
 * @author leyland
 */
final class PdfBoxStreamCache implements AutoCloseable {

    /** 实际管理随机访问缓冲区和临时文件的 PDFBox 缓存。 */
    private final ScratchFile scratchFile;

    /** 负责登记和释放缓存预算的请求工作区。 */
    private final PdfRenderWorkspace workspace;

    /** 工作区为本缓存保留的最大字节数。 */
    private final long reservedBytes;

    /** 关闭后不再创建新的文档视图。 */
    private boolean closed;

    /** 创建已由工作区预留容量的文件缓存。 */
    PdfBoxStreamCache(
            Path directory, long maxBytes, PdfRenderWorkspace workspace) throws IOException {
        MemoryUsageSetting setting =
                MemoryUsageSetting.setupTempFileOnly(maxBytes).setTempDir(directory.toFile());
        this.scratchFile = new ScratchFile(setting);
        this.workspace = workspace;
        this.reservedBytes = maxBytes;
    }

    /**
     * 返回供目标文档和源文档共同使用的缓存工厂。
     *
     * @return 每次调用都创建一个非共享关闭状态的轻量视图
     */
    RandomAccessStreamCache.StreamCacheCreateFunction factory() {
        return this::createView;
    }

    /** 创建只管理自身生命周期、不关闭底层请求缓存的视图。 */
    private RandomAccessStreamCache createView() throws IOException {
        if (closed) {
            throw new IOException("PDFBox 文件缓存已经关闭");
        }
        return new CacheView(scratchFile);
    }

    /** 关闭底层缓存并归还工作区预算。 */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            scratchFile.close();
        } catch (IOException exception) {
            failure = exception;
        } finally {
            workspace.releasePdfBoxCache(this, reservedBytes);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** PDFBox 文档关闭视图时，不影响同次组装中的其他文档。 */
    private static final class CacheView implements RandomAccessStreamCache {

        /** 真正分配随机访问缓冲区的请求级缓存。 */
        private final ScratchFile scratchFile;

        /** 当前文档结束后拒绝再次创建缓冲区。 */
        private boolean closed;

        private CacheView(ScratchFile scratchFile) {
            this.scratchFile = scratchFile;
        }

        /** @return 从共享请求缓存分配的独立随机访问缓冲区 */
        @Override
        public RandomAccess createBuffer() throws IOException {
            if (closed) {
                throw new IOException("PDFBox 文件缓存视图已经关闭");
            }
            return scratchFile.createBuffer();
        }

        /** 只结束当前文档的视图，具体缓冲区由 PDFBox 文档自行关闭。 */
        @Override
        public void close() {
            closed = true;
        }
    }
}
