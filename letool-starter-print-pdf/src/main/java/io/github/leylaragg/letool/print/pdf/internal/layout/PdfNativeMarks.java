package io.github.leylaragg.letool.print.pdf.internal.layout;

import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.newtable.TableRowBox;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.ContentLimitContainer;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.InlineText;
import com.openhtmltopdf.render.PageBox;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从实际排版行与跨页框提取最终绘制坐标。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfNativeMarks {
    /** 下划线在文本基线下方的偏移，单位为点。 */
    private static final float UNDERLINE_OFFSET_POINTS = 2;

    private PdfNativeMarks() {
    }

    /**
     * 一条 PDF 下划线的位置。
     *
     * @param page 从零开始的物理页索引
     * @param left 左端横坐标，单位为点
     * @param right 右端横坐标，单位为点
     * @param y 纵坐标，原点在页面左下角，单位为点
     */
    record Line(int page, float left, float right, float y) {
    }

    /**
     * 本轮排版提取的绘制位置快照。
     *
     * @param underlines 按物理页和文本基线归并的下划线
     * @param frameBottoms 每页正文表格框底的最低纵坐标，使用 PDF 点坐标
     */
    record Marks(List<Line> underlines, Map<Integer, Float> frameBottoms) {
    }

    /**
     * 从最终布局树采集实际位置，返回集合不随后续遍历变化。
     *
     * @param renderer 已完成布局的本轮渲染器
     * @return 下划线和正文框底的位置快照
     */
    static Marks collect(PdfBoxRenderer renderer) {
        Map<String, Line> lines = new LinkedHashMap<>();
        Map<Integer, Float> bottoms = new LinkedHashMap<>();
        visit(renderer.getRootBox(), renderer, lines, bottoms);
        return new Marks(List.copyOf(lines.values()), Map.copyOf(bottoms));
    }

    /** 遍历实际行盒与跨页表格行，将从页面顶部计量的原生坐标换为 PDF 坐标。 */
    private static void visit(Box box, PdfBoxRenderer renderer, Map<String, Line> lines, Map<Integer, Float> bottoms) {
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        float dots = renderer.getDotsPerPoint();
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof InlineText text && underlined(inline.getElement()) && text.getWidth() > 0) {
                    int baseline = inline.getAbsY() + inline.getBaseline();
                    PageBox page = renderer.getRootBox().getLayer().getPage(context, baseline);
                    float left = (inline.getAbsX() + text.getX() + page.getMarginBorderPadding(context,
                        CalculatedStyle.LEFT)) / dots;
                    float y = (page.getBottom() - baseline + page.getMarginBorderPadding(context,
                        CalculatedStyle.BOTTOM)) / dots - UNDERLINE_OFFSET_POINTS;
                    // 同页同基线的片段归并为完整行，换行后自然形成独立下划线。
                    String key = page.getPageNo() + ":" + baseline;
                    Line previous = lines.get(key);
                    lines.put(key, new Line(page.getPageNo(), previous == null ? left : Math.min(previous.left(), left),
                        previous == null ? left + text.getWidth() / dots : Math.max(previous.right(),
                        left + text.getWidth() / dots), y));
                } else if (child instanceof Box nested) {
                    visit(nested, renderer, lines, bottoms);
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                visit(child, renderer, lines, bottoms);
            }
        }

        // 跨页行按每页实际内容下界记录框底，供图例逐页定位。
        if (box instanceof TableRowBox row && inBody(row.getElement())) {
            ContentLimitContainer limits = row.getContentLimitContainer();
            if (limits != null) {
                for (int pageNumber = limits.getInitialPageNo(); pageNumber <= limits.getLastPageNo();
                    pageNumber++) {
                    PageBox page = renderer.getRootBox().getLayer().getPages().get(pageNumber);
                    int bottom = pageNumber == limits.getLastPageNo() ? row.getAbsY() + row.getHeight()
                        : limits.getContentLimit(pageNumber).getBottom() + row.getExtraSpaceBottom();
                    float y = (page.getBottom() - bottom + page.getMarginBorderPadding(context,
                        CalculatedStyle.BOTTOM)) / dots;
                    bottoms.merge(pageNumber, y, Math::min);
                }
            }
        }
    }

    /** 组合节点的下划线标记可由祖先元素继承。 */
    private static boolean underlined(Element element) {
        for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
            if ("true".equals(current.getAttribute("data-native-underline"))) {
                return true;
            }
        }
        return false;
    }

    /** 只采集正文区域的表格框底，排除页眉和页脚结构。 */
    private static boolean inBody(Element element) {
        for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
            if ("letool-body-region".equals(current.getAttribute("id"))) {
                return true;
            }
        }
        return false;
    }
}
