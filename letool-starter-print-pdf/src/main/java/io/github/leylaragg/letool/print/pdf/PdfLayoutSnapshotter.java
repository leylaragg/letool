package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.PageBox;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从仍处于打开状态的排版器提取纯坐标快照。
 *
 * @author leyland
 */
final class PdfLayoutSnapshotter {

    /** 在排版器关闭前提取目标和链接源的可见坐标。 */
    PdfLayoutSnapshot snapshot(PdfBoxRenderer renderer, PdfRenderIds ids) {
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        Map<String, PdfLayoutSnapshot.Position> targets = new LinkedHashMap<>();
        for (String id : ids.targetIds()) {
            Box box = renderer.getSharedContext().getBoxById(id);
            if (box != null) {
                PdfLayoutSnapshot.Position position = firstVisible(renderer, context, box);
                if (position != null) {
                    targets.put(id, position);
                }
            }
        }
        Map<String, List<PdfLayoutSnapshot.Position>> sources = new LinkedHashMap<>();
        for (String sourceId : ids.linkIds().values()) {
            Box box = renderer.getSharedContext().getBoxById(sourceId);
            if (box != null) {
                sources.put(sourceId, visibleFragments(renderer, context, box));
            }
        }
        float dotsPerPoint = renderer.getDotsPerPoint();
        float headerHeight = regionHeight(renderer, PdfXhtmlRenderer.HEADER_REGION_ID, dotsPerPoint);
        float footerHeight = regionHeight(renderer, PdfXhtmlRenderer.FOOTER_REGION_ID, dotsPerPoint);
        return new PdfLayoutSnapshot(targets, sources, headerHeight, footerHeight);
    }

    /** 读取 running element 原始盒子的高度，缺少区域时返回零。 */
    private float regionHeight(PdfBoxRenderer renderer, String id, float dotsPerPoint) {
        Box box = renderer.getSharedContext().getBoxById(id);
        return box == null ? 0F : box.getHeight() / dotsPerPoint;
    }

    /** 目标只使用第一个可见片段，目录和跳转落点保持一致。 */
    private PdfLayoutSnapshot.Position firstVisible(
            PdfBoxRenderer renderer, LayoutContext context, Box box) {
        List<PdfLayoutSnapshot.Position> fragments = visibleFragments(renderer, context, box);
        return fragments.isEmpty() ? null : fragments.get(0);
    }

    /** 链接标签跨页时保留每个页面上的可见交集。 */
    private List<PdfLayoutSnapshot.Position> visibleFragments(
            PdfBoxRenderer renderer, LayoutContext context, Box box) {
        Rectangle bounds = new Rectangle(
                box.getAbsX(), box.getAbsY(), box.getEffectiveWidth(), box.getHeight());
        List<PdfLayoutSnapshot.Position> positions = new ArrayList<>();
        for (PageBox page : renderer.getRootBox().getLayer().getPages()) {
            Rectangle visible = bounds.intersection(pageContentBounds(context, page));
            if (!visible.isEmpty()) {
                positions.add(new PdfLayoutSnapshot.Position(
                        page.getPageNo(), toPdfRectangle(renderer, context, page, visible)));
            }
        }
        return List.copyOf(positions);
    }

    /** OpenHTMLToPDF 返回页内内容框，这里补上页面在整份文档中的纵向偏移。 */
    private Rectangle pageContentBounds(LayoutContext context, PageBox page) {
        Rectangle bounds = page.getDocumentCoordinatesContentBounds(context);
        bounds.translate(0, page.getTop());
        return bounds;
    }

    /** 沿用 OpenHTMLToPDF 的页面边距和坐标原点换算。 */
    private PDRectangle toPdfRectangle(
            PdfBoxRenderer renderer, LayoutContext context, PageBox page, Rectangle visible) {
        float dotsPerPoint = renderer.getDotsPerPoint();
        float x = (visible.x + page.getMarginBorderPadding(context, CalculatedStyle.LEFT))
                / dotsPerPoint;
        float y = (page.getBottom() - visible.y - visible.height
                + page.getMarginBorderPadding(context, CalculatedStyle.BOTTOM)) / dotsPerPoint;
        return new PDRectangle(x, y, visible.width / dotsPerPoint, visible.height / dotsPerPoint);
    }
}
