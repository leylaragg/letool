package io.github.leylaragg.letool.print.pdf;

import java.nio.file.Path;

/**
 * 一个排版单元保存完成后的文件、页数和坐标快照。
 *
 * @author leyland
 */
final class PdfUnitResult {
    private final Path file;
    private final int pageCount;
    private final PdfLayoutSnapshot snapshot;

    /** 汇总单元文件及合并阶段需要的布局信息。 */
    PdfUnitResult(Path file, int pageCount, PdfLayoutSnapshot snapshot) {
        this.file = file;
        this.pageCount = pageCount;
        this.snapshot = snapshot;
    }

    /** @return 已登记到工作区的单元文件 */
    Path file() {
        return file;
    }

    /** @return 当前单元的物理页数 */
    int pageCount() {
        return pageCount;
    }

    /** @return 脱离排版器保存的坐标快照 */
    PdfLayoutSnapshot snapshot() {
        return snapshot;
    }
}
