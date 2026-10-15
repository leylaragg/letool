package io.github.leylaragg.letool.print.pdf;

/**
 * 目录排版使用的标题和稳定目标。
 *
 * @author leyland
 */
final class PdfTocEntry {
    private final int level;
    private final String title;
    private final String targetId;

    /** 保存目录行需要的层级、文字和实际布局目标。 */
    PdfTocEntry(int level, String title, String targetId) {
        this.level = level;
        this.title = title;
        this.targetId = targetId;
    }

    /** @return 目录层级 */
    int level() {
        return level;
    }

    /** @return 目录中展示的标题文字 */
    String title() {
        return title;
    }

    /** @return 标题对应的稳定布局目标 */
    String targetId() {
        return targetId;
    }
}
