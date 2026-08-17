package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.DocumentModel;
import io.github.leylaragg.letool.print.document.DocumentTraversal;
import io.github.leylaragg.letool.print.document.node.AnnotationNode;
import io.github.leylaragg.letool.print.document.node.AnnotationPlacement;
import io.github.leylaragg.letool.print.document.node.AnnotationType;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.PageBox;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把通用批注节点定位到排版后的首个可见片段并写入 PDF。
 *
 * @author leyland
 */
final class PdfAnnotationWriter {

    /** 单个 PDF 最多写入的批注数量。 */
    static final int MAX_ANNOTATIONS = 1_000;

    /** 微米转换为 PDF 点时使用的物理比例。 */
    private static final float POINTS_PER_MICROMETER = 72F / 25_400F;

    /** 自由文本框使用的固定字号。 */
    private static final float FREE_TEXT_FONT_SIZE = 10F;

    /** 自由文本框内边距。 */
    private static final float FREE_TEXT_PADDING = 3F;

    /** 页面范围比较允许的浮点误差。 */
    private static final float GEOMETRY_EPSILON = 0.01F;

    /** 便签和文本框统一使用的浅黄色。 */
    private static final PDColor ANNOTATION_COLOR = new PDColor(
            new float[]{1F, 0.88F, 0.28F}, PDDeviceRGB.INSTANCE);

    /** 宿主字体定义的不可变快照。 */
    private final List<PdfFont> fonts;

    /**
     * 创建无共享渲染状态的批注写入器。
     *
     * @param fonts PDF 渲染器已冻结的宿主字体
     */
    PdfAnnotationWriter(List<PdfFont> fonts) {
        this.fonts = List.copyOf(Objects.requireNonNull(fonts, "fonts 不能为空"));
    }

    /**
     * 从文档树提取批注并在排版前执行数量治理。
     *
     * @param document 已完成结构校验的文档
     * @return 保持文档顺序的批注快照
     */
    List<AnnotationNode> collect(DocumentModel document) {
        List<AnnotationNode> annotations = DocumentTraversal.depthFirst(document).stream()
                .filter(AnnotationNode.class::isInstance)
                .map(AnnotationNode.class::cast)
                .toList();
        if (annotations.size() > MAX_ANNOTATIONS) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注数量超过 " + MAX_ANNOTATIONS);
        }
        return annotations;
    }

    /**
     * 在 PDF 保存前解析全部目标和矩形，再一次性写入批注。
     *
     * @param renderer 已完成页面绘制的排版器
     * @param pdf 尚未保存的 PDF 文档
     * @param annotations 待写入批注
     * @throws IOException 字体或 PDF 外观写入失败时抛出
     */
    void write(
            PdfBoxRenderer renderer,
            PDDocument pdf,
            List<AnnotationNode> annotations) throws IOException {
        if (annotations.isEmpty()) {
            return;
        }
        List<ResolvedAnnotation> resolved = resolveAll(renderer, pdf, annotations);
        PDFont freeTextFont = requiresFreeText(annotations) ? loadAppearanceFont(pdf) : null;
        for (ResolvedAnnotation item : resolved) {
            PDAnnotation annotation = item.node().type() == AnnotationType.TEXT_NOTE
                    ? createTextNote(pdf, item)
                    : createFreeText(pdf, item, freeTextFont);
            item.page().getAnnotations().add(annotation);
        }
    }

    /**
     * 使用合并后的全局目标位置写入跨单元批注。
     *
     * @param pdf 最终 PDF
     * @param annotations 待写入批注
     * @param targets 已校验的全局目标位置
     * @throws IOException 字体或外观写入失败时抛出
     */
    void writeMerged(
            PDDocument pdf,
            List<AnnotationNode> annotations,
            java.util.Map<String, PdfNavigationWriter.GlobalPosition> targets) throws IOException {
        if (annotations.isEmpty()) {
            return;
        }
        List<ResolvedAnnotation> resolved = new ArrayList<>(annotations.size());
        for (AnnotationNode annotation : annotations) {
            PdfNavigationWriter.GlobalPosition target = targets.get(annotation.targetId());
            if (target == null || target.pageIndex() < 0 || target.pageIndex() >= pdf.getNumberOfPages()) {
                throw PrintValidationException.invalidDocument("PDF 批注目标没有可见页面");
            }
            PDPage page = pdf.getPage(target.pageIndex());
            PDRectangle rectangle = place(annotation, target.rectangle());
            requireInsidePage(rectangle, page.getCropBox(), annotation.targetId());
            requireUsableFreeTextRectangle(annotation, rectangle);
            resolved.add(new ResolvedAnnotation(annotation, page, rectangle));
        }
        PDFont freeTextFont = requiresFreeText(annotations) ? loadAppearanceFont(pdf) : null;
        for (ResolvedAnnotation item : resolved) {
            PDAnnotation value = item.node().type() == AnnotationType.TEXT_NOTE
                    ? createTextNote(pdf, item) : createFreeText(pdf, item, freeTextFont);
            item.page().getAnnotations().add(value);
        }
    }

    /** 在修改 PDF 前完成所有目标、页面和矩形校验。 */
    private List<ResolvedAnnotation> resolveAll(
            PdfBoxRenderer renderer,
            PDDocument pdf,
            List<AnnotationNode> annotations) {
        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        List<ResolvedAnnotation> resolved = new ArrayList<>(annotations.size());
        for (AnnotationNode annotation : annotations) {
            resolved.add(resolve(renderer, pdf, context, annotation));
        }
        return List.copyOf(resolved);
    }

    /** 将一个逻辑目标换算为页面内的最终批注矩形。 */
    private ResolvedAnnotation resolve(
            PdfBoxRenderer renderer,
            PDDocument pdf,
            LayoutContext context,
            AnnotationNode annotation) {
        Box target = renderer.getSharedContext().getBoxById(annotation.targetId());
        if (target == null) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注目标不存在：" + annotation.targetId());
        }
        PageBox pageBox = renderer.getRootBox().getLayer().getFirstPage(context, target);
        if (pageBox == null || pageBox.getPageNo() < 0 || pageBox.getPageNo() >= pdf.getNumberOfPages()) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注目标没有可见页面：" + annotation.targetId());
        }
        Rectangle visible = firstVisibleBounds(context, target, pageBox, annotation.targetId());
        PDRectangle targetRectangle = toPdfRectangle(renderer, context, pageBox, visible);
        PDPage page = pdf.getPage(pageBox.getPageNo());
        PDRectangle annotationRectangle = place(annotation, targetRectangle);
        requireInsidePage(annotationRectangle, page.getCropBox(), annotation.targetId());
        requireUsableFreeTextRectangle(annotation, annotationRectangle);
        return new ResolvedAnnotation(annotation, page, annotationRectangle);
    }

    /** 取目标盒与其第一页内容区域的交集，避免跨页节点落到后续页面。 */
    private Rectangle firstVisibleBounds(
            LayoutContext context,
            Box target,
            PageBox page,
            String targetId) {
        Rectangle targetBounds = new Rectangle(
                target.getAbsX(),
                target.getAbsY(),
                target.getEffectiveWidth(),
                target.getHeight());
        Rectangle visible = targetBounds.intersection(
                page.getDocumentCoordinatesContentBounds(context));
        if (visible.isEmpty()) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注目标没有可见区域：" + targetId);
        }
        return visible;
    }

    /** 按 OpenHTMLToPDF 的页面原点规则把布局坐标转换为 PDF 点坐标。 */
    private PDRectangle toPdfRectangle(
            PdfBoxRenderer renderer,
            LayoutContext context,
            PageBox page,
            Rectangle visible) {
        float dotsPerPoint = renderer.getDotsPerPoint();
        float x = (visible.x + page.getMarginBorderPadding(context, CalculatedStyle.LEFT))
                / dotsPerPoint;
        float y = (page.getBottom() - visible.y - visible.height
                + page.getMarginBorderPadding(context, CalculatedStyle.BOTTOM)) / dotsPerPoint;
        return new PDRectangle(
                x,
                y,
                visible.width / dotsPerPoint,
                visible.height / dotsPerPoint);
    }

    /** 以目标可见矩形的受控角点为锚点放置批注。 */
    private PDRectangle place(AnnotationNode annotation, PDRectangle target) {
        float width = annotation.widthMicrometers() * POINTS_PER_MICROMETER;
        float height = annotation.heightMicrometers() * POINTS_PER_MICROMETER;
        float offsetX = annotation.offsetXMicrometers() * POINTS_PER_MICROMETER;
        float offsetY = annotation.offsetYMicrometers() * POINTS_PER_MICROMETER;
        boolean right = annotation.placement() == AnnotationPlacement.TOP_RIGHT
                || annotation.placement() == AnnotationPlacement.BOTTOM_RIGHT;
        boolean top = annotation.placement() == AnnotationPlacement.TOP_LEFT
                || annotation.placement() == AnnotationPlacement.TOP_RIGHT;
        float x = (right ? target.getUpperRightX() - width : target.getLowerLeftX()) + offsetX;
        float y = (top ? target.getUpperRightY() - height : target.getLowerLeftY()) + offsetY;
        return new PDRectangle(x, y, width, height);
    }

    /** 拒绝越过页面裁剪框的矩形，不替模板静默调整位置。 */
    private void requireInsidePage(PDRectangle rectangle, PDRectangle page, String targetId) {
        if (rectangle.getLowerLeftX() < page.getLowerLeftX() - GEOMETRY_EPSILON
                || rectangle.getLowerLeftY() < page.getLowerLeftY() - GEOMETRY_EPSILON
                || rectangle.getUpperRightX() > page.getUpperRightX() + GEOMETRY_EPSILON
                || rectangle.getUpperRightY() > page.getUpperRightY() + GEOMETRY_EPSILON) {
            throw PrintValidationException.invalidDocument(
                    "PDF 批注矩形超出页面范围：" + targetId);
        }
    }

    /** 自由文本框必须留出足够空间生成可读外观。 */
    private void requireUsableFreeTextRectangle(
            AnnotationNode annotation,
            PDRectangle rectangle) {
        if (annotation.type() == AnnotationType.FREE_TEXT
                && (rectangle.getWidth() <= FREE_TEXT_PADDING * 2 + FREE_TEXT_FONT_SIZE
                || rectangle.getHeight() <= FREE_TEXT_PADDING * 2 + FREE_TEXT_FONT_SIZE)) {
            throw PrintValidationException.invalidDocument("自由文本框批注尺寸过小");
        }
    }

    /** 创建关闭弹窗、可打印并带固定图标外观的便签。 */
    private PDAnnotationText createTextNote(
            PDDocument pdf,
            ResolvedAnnotation item) {
        PDAnnotationText annotation = new PDAnnotationText();
        applyCommonProperties(annotation, item);
        annotation.setName(PDAnnotationText.NAME_NOTE);
        annotation.setOpen(false);
        annotation.constructAppearances(pdf);
        return annotation;
    }

    /** 创建使用宿主嵌入字体的自由文本框。 */
    private PDAnnotationFreeText createFreeText(
            PDDocument pdf,
            ResolvedAnnotation item,
            PDFont font) throws IOException {
        PDAnnotationFreeText annotation = new PDAnnotationFreeText();
        applyCommonProperties(annotation, item);
        PDAppearanceStream appearance = createFreeTextAppearance(
                pdf, item.rectangle(), item.node().content(), font);
        COSName fontName = appearance.getResources().getFontNames().iterator().next();
        annotation.setDefaultAppearance(
                "/" + fontName.getName() + " " + FREE_TEXT_FONT_SIZE + " Tf 0 g");
        annotation.setDefaultStyleString("font-size:10pt;color:#000000");
        PDAppearanceDictionary dictionary = new PDAppearanceDictionary();
        dictionary.setNormalAppearance(appearance);
        annotation.setAppearance(dictionary);
        return annotation;
    }

    /** 写入两类批注共享的正文、作者、颜色和页面矩形。 */
    private void applyCommonProperties(PDAnnotation annotation, ResolvedAnnotation item) {
        annotation.setRectangle(item.rectangle());
        annotation.setContents(item.node().content());
        annotation.setPrinted(true);
        annotation.setColor(ANNOTATION_COLOR);
        if (annotation instanceof PDAnnotationMarkup markup) {
            markup.setTitlePopup(item.node().author());
            markup.setSubject(item.node().type() == AnnotationType.TEXT_NOTE
                    ? "Letool text note" : "Letool free text");
        }
    }

    /** 使用固定颜色和排版规则绘制自由文本框普通外观。 */
    private PDAppearanceStream createFreeTextAppearance(
            PDDocument pdf,
            PDRectangle rectangle,
            String content,
            PDFont font) throws IOException {
        float width = rectangle.getWidth();
        float height = rectangle.getHeight();
        PDAppearanceStream appearance = new PDAppearanceStream(pdf);
        appearance.setBBox(new PDRectangle(width, height));
        PDResources resources = new PDResources();
        resources.add(font);
        appearance.setResources(resources);
        try (PDPageContentStream stream = new PDPageContentStream(pdf, appearance)) {
            stream.setNonStrokingColor(1F, 0.98F, 0.78F);
            stream.addRect(0, 0, width, height);
            stream.fill();
            stream.setStrokingColor(0.72F, 0.58F, 0.12F);
            stream.setLineWidth(0.8F);
            stream.addRect(0.4F, 0.4F, width - 0.8F, height - 0.8F);
            stream.stroke();
            writeFreeText(stream, font, content, width, height);
        }
        return appearance;
    }

    /** 在固定内边距内逐字符换行，超出高度的正文仍完整保存在 Contents。 */
    private void writeFreeText(
            PDPageContentStream stream,
            PDFont font,
            String content,
            float width,
            float height) throws IOException {
        float lineHeight = FREE_TEXT_FONT_SIZE * 1.25F;
        int maxLines = Math.max(1, (int) ((height - FREE_TEXT_PADDING * 2) / lineHeight));
        List<String> lines = wrap(content, font, width - FREE_TEXT_PADDING * 2, maxLines);
        stream.beginText();
        stream.setNonStrokingColor(0F, 0F, 0F);
        stream.setFont(font, FREE_TEXT_FONT_SIZE);
        stream.setLeading(lineHeight);
        stream.newLineAtOffset(FREE_TEXT_PADDING, height - FREE_TEXT_PADDING - FREE_TEXT_FONT_SIZE);
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                stream.newLine();
            }
            stream.showText(lines.get(index));
        }
        stream.endText();
    }

    /** 按字体实际宽度生成不超过指定行数的外观文本。 */
    private List<String> wrap(
            String content,
            PDFont font,
            float maxWidth,
            int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int[] codePoints = content.codePoints().toArray();
        for (int codePoint : codePoints) {
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\n') {
                lines.add(current.toString());
                current.setLength(0);
            } else {
                String character = new String(Character.toChars(codePoint));
                String candidate = current + character;
                float candidateWidth = font.getStringWidth(candidate) / 1_000F * FREE_TEXT_FONT_SIZE;
                if (!current.isEmpty() && candidateWidth > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
            }
            if (lines.size() >= maxLines) {
                return List.copyOf(lines);
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    /** 判断本次渲染是否需要额外加载自由文本外观字体。 */
    private boolean requiresFreeText(List<AnnotationNode> annotations) {
        return annotations.stream().anyMatch(annotation -> annotation.type() == AnnotationType.FREE_TEXT);
    }

    /** 从最终回退字体或首个宿主字体加载可嵌入外观字体。 */
    private PDFont loadAppearanceFont(PDDocument pdf) throws IOException {
        PdfFont selected = fonts.stream()
                .filter(PdfFont::fallback)
                .findFirst()
                .orElseGet(() -> fonts.isEmpty() ? null : fonts.get(0));
        if (selected == null) {
            throw PrintValidationException.invalidDocument("自由文本框批注需要宿主字体");
        }
        try (InputStream input = selected.openStream()) {
            return PDType0Font.load(pdf, input, true);
        }
    }

    /** 单次渲染使用的已校验批注位置。 */
    private static final class ResolvedAnnotation {

        /** 通用批注节点。 */
        private final AnnotationNode node;

        /** 批注所在页面。 */
        private final PDPage page;

        /** 页面坐标中的批注矩形。 */
        private final PDRectangle rectangle;

        /** 保存一个已经完成校验的位置。 */
        private ResolvedAnnotation(AnnotationNode node, PDPage page, PDRectangle rectangle) {
            this.node = node;
            this.page = page;
            this.rectangle = rectangle;
        }

        /** @return 通用批注节点 */
        private AnnotationNode node() {
            return node;
        }

        /** @return 批注所在页面 */
        private PDPage page() {
            return page;
        }

        /** @return 页面内批注矩形 */
        private PDRectangle rectangle() {
            return rectangle;
        }
    }
}
