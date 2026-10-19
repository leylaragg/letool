package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.pdf.testsupport.RenderedPdf;

import io.github.leylaragg.letool.print.api.RenderOptions;
import io.github.leylaragg.letool.print.document.DocumentMetadata;
import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.node.ParagraphNode;
import io.github.leylaragg.letool.print.document.node.ShapeNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/** 小尺寸原生标记仍完整落在声明几何中。 */
class PdfSmallShapeTest {
    @Test
    void shouldKeepSelectedMarksAndUnderlineInsideSmallNativeShapes() throws Exception {
        DocumentModel document = DocumentModel.singleSequence(DocumentMetadata.empty(), PageLayout.a4Portrait(),
            List.of(new ParagraphNode("", List.of(
            new ShapeNode(ShapeNode.Kind.CIRCLE, 2, 2, 0.25, true, 1, 0.8),
            new ShapeNode(ShapeNode.Kind.SQUARE, 3, 3, 0.25, true, 1, 1.5),
            new ShapeNode(ShapeNode.Kind.UNDERLINE, 10, 2, 0.25, false, 1, 1)))));
        RenderedPdf rendered = RenderedPdf.render(new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of())),
            document, new RenderOptions(3, 1024 * 1024, 3 * 1024 * 1024, false));
        try (PDDocument pdf = Loader.loadPDF(rendered.content())) {
            Bounds engine = new Bounds(pdf.getPage(0));
            engine.processPage(pdf.getPage(0));
            assertThat(engine.paths).hasSize(5);
            Rectangle2D circle = engine.paths.get(0);
            Rectangle2D dot = engine.paths.get(1);
            Rectangle2D square = engine.paths.get(2);
            Rectangle2D cross = engine.paths.get(3);
            Rectangle2D underline = engine.paths.get(4);
            assertThat(circle.getWidth()).isCloseTo(2, offset(0.06));
            assertThat(dot.getWidth()).isCloseTo(0.8, offset(0.06));
            assertThat(circle.contains(dot)).isTrue();
            assertThat(square.getWidth()).isCloseTo(3, offset(0.06));
            assertThat(cross.getWidth()).isCloseTo(1.5, offset(0.06));
            assertThat(square.contains(cross)).isTrue();
            assertThat(underline.getMinY()).isCloseTo(circle.getCenterY(), offset(0.06));
            assertThat(underline.getWidth()).isCloseTo(10, offset(0.06));
        }
    }

    /** 读取最终正文向量路径，供分段回归确认行内控件仍被实际绘制。 */
    static List<Rectangle2D> paintedPaths(PDDocument pdf) throws IOException {
        List<Rectangle2D> paths = new ArrayList<>();
        for (PDPage page : pdf.getPages()) {
            Bounds engine = new Bounds(page);
            engine.processPage(page);
            paths.addAll(engine.paths);
        }
        return paths;
    }

    private static final class Bounds extends PDFGraphicsStreamEngine {
        private final List<Rectangle2D> paths = new ArrayList<>();
        private Rectangle2D.Double current;
        private Point2D point;
        private Bounds(PDPage page) {
            super(page);
        }
        private void add(float x, float y) {
            point = new Point2D.Float(x, y);
            if (current == null) { current = new Rectangle2D.Double(x, y, 0, 0); } else { current.add(x, y); }
        }
        private void finish() {
            if (current != null) {
                paths.add(current);
            }
            current = null;
        }
        @Override public void moveTo(float x, float y) {
            add(x, y);
        }
        @Override public void lineTo(float x, float y) {
            add(x, y);
        }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            add(x1, y1); add(x2, y2); add(x3, y3);
        }
        @Override public void appendRectangle(Point2D a, Point2D b, Point2D c, Point2D d) {
            add((float) a.getX(), (float) a.getY()); add((float) b.getX(), (float) b.getY());
            add((float) c.getX(), (float) c.getY()); add((float) d.getX(), (float) d.getY());
        }
        @Override public Point2D getCurrentPoint() {
            return point;
        }
        @Override public void closePath() { }
        @Override public void endPath() {
            current = null;
        }
        @Override public void strokePath() {
            finish();
        }
        @Override public void fillPath(int windingRule) {
            finish();
        }
        @Override public void fillAndStrokePath(int windingRule) {
            finish();
        }
        @Override public void clip(int windingRule) { }
        @Override public void drawImage(PDImage image) {
            throw new AssertionError("Native shape produced image");
        }
        @Override public void shadingFill(org.apache.pdfbox.cos.COSName name) { }
    }
}
