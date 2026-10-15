package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.InlineText;
import com.openhtmltopdf.render.PageBox;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.BlockNode;
import io.github.leylaragg.letool.print.document.node.ContentsEntryNode;
import io.github.leylaragg.letool.print.document.node.ContentsTitleNode;
import io.github.leylaragg.letool.print.document.node.TableOfContentsLayout;
import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.document.style.StyleSheet;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按实际目录文字行调整基线，并保存原生点线坐标。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfNativeContents {

    /** 一级目录使用分组行高及分隔线。 */
    private static final int GROUP_LEVEL = 1;

    /** 布局引擎整数坐标允许的取整误差。 */
    private static final int LAYOUT_TOLERANCE_DOTS = 1;

    /** PDF 颜色分量使用 0 到 1，模型使用 0 到 255。 */
    private static final float RGB_COMPONENT_MAX = 255f;

    /** PDF 线帽：0 为平头，1 为圆头。 */
    private static final int FLAT_LINE_CAP = 0;
    private static final int ROUND_LINE_CAP = 1;

    /**
     * 一段实际文字的占位，坐标单位为布局引擎坐标。
     *
     * @param baseline 文字基线
     * @param left 文字起点
     * @param right 文字终点
     */
    private record Run(int baseline, int left, int right) {
    }

    /**
     * 布局稳定后追加的 PDF 线段，长度与位置单位均为 pt。
     *
     * @param page 从 0 开始的 PDF 页号
     * @param left 横线起点
     * @param right 横线终点
     * @param y PDF 自页面底部向上的纵坐标
     * @param width 线宽
     * @param dash 虚线段长度；0 表示实线
     * @param gap 虚线段间距
     * @param color 线条颜色
     */
    record Rule(int page, float left, float right, float y, float width, float dash, float gap,
            DocumentColor color) {
    }

    private PdfNativeContents() {
    }

    /**
     * 将模板声明的基线位置转换为实测后的页面、标题和条目样式。
     *
     * @param renderer 已完成初次布局的渲染器
     * @param xhtml 初次布局使用的 XHTML
     * @param blocks 当前页面序列的内容
     * @param ids 目录链接与生成节点 ID 的映射
     * @return 带基线修正样式的 XHTML；没有原生目录时返回原文
     * @throws PrintValidationException 标题和首行无法按配置完整排布
     */
    static String adjust(PdfBoxRenderer renderer, String xhtml, List<BlockNode> blocks, PdfRenderIds ids) {
        ContentsTitleNode title = DocumentTraversal.depthFirstBlocks(blocks).stream()
                .filter(ContentsTitleNode.class::isInstance).map(ContentsTitleNode.class::cast).findFirst()
                .orElse(null);
        if (title == null) {
            return xhtml;
        }

        // 首页面顶包含目录标题，续页只包含条目，分别按实测基线反推上边距。
        TableOfContentsLayout layout = title.layout();
        float dots = renderer.getDotsPerPoint();
        Box titleBox = renderer.getSharedContext().getBoxById(title.id());
        double titleBaseline = (firstRun(titleBox).baseline() - titleBox.getAbsY()) / dots;
        List<ContentsEntryNode> entries = entries(blocks);
        double firstBaseline = entries.isEmpty() ? 0 : entryBaseline(renderer, ids, entries.get(0), dots);
        StringBuilder css = new StringBuilder("/*lt-contents*/@page{margin-top:")
                .append(Math.max(0, layout.continuationBaseline() - firstBaseline)).append("pt;}")
                .append("@page:first{margin-top:").append(layout.titleBaseline() - titleBaseline).append("pt;}");
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        double gap = entries.isEmpty() ? 0 : layout.firstEntryBaseline() - layout.titleBaseline()
                - ((titleBox.getHeight() - titleBox.getMargin(context).bottom()) / dots - titleBaseline)
                - firstBaseline;
        if (gap < 0) {
            throw PrintValidationException.invalidDocument("目录标题与首行没有合法间距：gap=" + gap
                    + ", titleH=" + titleBox.getHeight() / dots + ", titleBase=" + titleBaseline
                    + ", first=" + firstBaseline + ", margin=" + titleBox.getMargin(context).bottom() / dots);
        }
        css.append('#').append(title.id()).append("{margin-bottom:").append(gap).append("pt;}");

        // 长标签可能占多行；扩展条目高度，并让页码与标签最后一行对齐。
        for (ContentsEntryNode entry : entries) {
            String source = ids.sourceId(entry.link());
            Box row = renderer.getSharedContext().getBoxById(source);
            Box label = renderer.getSharedContext().getBoxById(source + "-label");
            Box page = renderer.getSharedContext().getBoxById(source + "-page");
            List<Run> runs = runs(label);
            int first = runs.stream().mapToInt(Run::baseline).min().orElseThrow();
            int last = runs.stream().mapToInt(Run::baseline).max().orElseThrow();
            double height = (entry.level() == GROUP_LEVEL ? layout.groupHeight() : layout.entryHeight())
                    + (last - first) / dots;
            double pageTop = (last - row.getAbsY() - firstRun(page).baseline() + page.getAbsY()) / dots;
            css.append('#').append(source).append("{height:").append(height).append("pt;}")
                    .append('#').append(source).append("-page{top:").append(pageTop).append("pt;}");
        }
        css.append("/*lt-contents-end*/");
        return xhtml.replace("</style>", css + "</style>");
    }

    /**
     * 从最终布局提取导引线和分组分隔线，避免用文字数量估算线条位置。
     *
     * @param renderer 已完成最终布局的渲染器
     * @param blocks 当前页面序列的内容
     * @param ids 目录链接与生成节点 ID 的映射
     * @param styles 用于解析目录文字颜色的样式表
     * @return 不可变线段列表
     * @throws PrintValidationException 完整目录条目跨越页面
     */
    static List<Rule> collect(PdfBoxRenderer renderer, List<BlockNode> blocks, PdfRenderIds ids, StyleSheet styles) {
        List<Rule> rules = new ArrayList<>();
        float dots = renderer.getDotsPerPoint();
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        for (ContentsEntryNode entry : entries(blocks)) {
            String source = ids.sourceId(entry.link());
            Box row = renderer.getSharedContext().getBoxById(source);
            Box label = renderer.getSharedContext().getBoxById(source + "-label");
            Box pageNumber = renderer.getSharedContext().getBoxById(source + "-page");
            List<Run> labelRuns = runs(label);
            int baseline = labelRuns.stream().mapToInt(Run::baseline).max().orElseThrow();
            int end = labelRuns.stream().filter(run -> run.baseline() == baseline)
                    .mapToInt(Run::right).max().orElseThrow();
            PageBox page = renderer.getRootBox().getLayer().getPage(context, baseline);
            if (page.getPageNo() != renderer.getRootBox().getLayer().getPage(context, row.getAbsY()).getPageNo()
                    || row.getAbsY() + row.getHeight() > page.getBottom() + LAYOUT_TOLERANCE_DOTS) {
                throw PrintValidationException.invalidDocument("完整目录条目没有合法单页位置");
            }

            // 原生布局向下计量，PDF 向上计量；补上页边距后转为当前页的 pt 坐标。
            float margin = page.getMarginBorderPadding(context, CalculatedStyle.LEFT) / dots;
            float y = (page.getBottom() - baseline
                    + page.getMarginBorderPadding(context, CalculatedStyle.BOTTOM)) / dots;
            TableOfContentsLayout layout = entry.layout();
            float left = end / dots + margin + (float) layout.leaderStart();
            float right = pageNumber.getAbsX() / dots + margin - (float) layout.leaderEnd();
            if (right > left && layout.leaderWidth() > 0) {
                rules.add(new Rule(page.getPageNo(), left, right,
                        y + (float) layout.leaderOffset(), (float) layout.leaderWidth(), (float) layout.leaderDash(),
                        (float) layout.leaderGap(), styles.resolveText(layout.textStyleName()).color()));
            }
            if (entry.level() == GROUP_LEVEL && layout.groupRuleWidth() > 0) {
                rules.add(new Rule(page.getPageNo(),
                        row.getAbsX() / dots + margin, (row.getAbsX() + row.getContentWidth()) / dots + margin,
                        y - (float) layout.groupRuleOffset(), (float) layout.groupRuleWidth(), 0, 0,
                        layout.groupRuleColor()));
            }
        }
        return List.copyOf(rules);
    }

    /**
     * 在各页现有内容后追加线段，每页内容流在写完后立即关闭。
     *
     * @param pdf 调用方持有的文档，本方法不关闭文档
     * @param rules 已换算为 PDF 坐标的线段
     * @throws IOException 创建、写入或关闭页面内容流失败
     */
    static void write(PDDocument pdf, List<Rule> rules) throws IOException {
        for (int page = 0; page < pdf.getNumberOfPages(); page++) {
            try (PDPageContentStream content = new PDPageContentStream(pdf, pdf.getPage(page),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                for (Rule rule : rules) {
                    if (rule.page() != page) {
                        continue;
                    }
                    content.setStrokingColor(rule.color().red() / RGB_COMPONENT_MAX,
                            rule.color().green() / RGB_COMPONENT_MAX, rule.color().blue() / RGB_COMPONENT_MAX);
                    content.setLineWidth(rule.width());

                    // 虚线使用圆头形成点线；实线清空虚线配置，避免继承上一条线的绘制状态。
                    content.setLineCapStyle(rule.dash() > 0 ? ROUND_LINE_CAP : FLAT_LINE_CAP);
                    content.setLineDashPattern(rule.dash() > 0
                            ? new float[] {rule.dash(), rule.gap()} : new float[0], 0);
                    content.moveTo(rule.left(), rule.y());
                    content.lineTo(rule.right(), rule.y());
                    content.stroke();
                }
            }
        }
    }

    /** 返回条目首行基线相对条目顶边的距离，单位为 pt。 */
    private static double entryBaseline(PdfBoxRenderer renderer, PdfRenderIds ids, ContentsEntryNode entry,
            float dots) {
        String source = ids.sourceId(entry.link());
        Box row = renderer.getSharedContext().getBoxById(source);
        Box label = renderer.getSharedContext().getBoxById(source + "-label");
        return (firstRun(label).baseline() - row.getAbsY()) / dots;
    }

    /** 保持文档顺序收集目录条目，包含嵌套块。 */
    private static List<ContentsEntryNode> entries(List<BlockNode> blocks) {
        return DocumentTraversal.depthFirstBlocks(blocks).stream().filter(ContentsEntryNode.class::isInstance)
                .map(ContentsEntryNode.class::cast).toList();
    }

    /** 取纵坐标最小的实际文字基线；目录标签由上游保证非空。 */
    private static Run firstRun(Box box) {
        return runs(box).stream().min(Comparator.comparingInt(Run::baseline)).orElseThrow();
    }

    /** 收集盒子内部占据实际宽度的文字段。 */
    private static List<Run> runs(Box box) {
        List<Run> result = new ArrayList<>();
        collectRuns(box, result);
        return result;
    }

    /** 递归遍历块级和行内盒子，不把零宽标记当作文字终点。 */
    private static void collectRuns(Box box, List<Run> result) {
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof InlineText text && text.getWidth() > 0) {
                    result.add(new Run(inline.getAbsY() + inline.getBaseline(), inline.getAbsX() + text.getX(),
                            inline.getAbsX() + text.getX() + text.getWidth()));
                } else if (child instanceof Box nested) {
                    collectRuns(nested, result);
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                collectRuns(child, result);
            }
        }
    }
}
