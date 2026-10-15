package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.newtable.TableRowBox;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.InlineText;
import com.openhtmltopdf.render.LineBox;
import com.openhtmltopdf.render.PageBox;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据原生排版结果拆分超页表格行，并在每个片段重建完整的重复侧。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfNativeFragmenter {

    /** 布局引擎整数坐标取整允许的一点误差，单位为引擎坐标。 */
    private static final int LAYOUT_TOLERANCE_DOTS = 1;

    /** 折叠表格边框预留的高度，单位为 pt。 */
    private static final double COLLAPSED_BORDER_ALLOWANCE = 1;

    /** 完整块与可用页高比较时的坐标舍入容差，单位为 pt。 */
    private static final double HEIGHT_TOLERANCE = 0.05;

    /** 连续累加行高时的分段容差，单位为 pt。 */
    private static final double CHUNK_TOLERANCE = 0.025;

    /**
     * 不允许从中间裁切的内容块。
     *
     * @param element 可克隆到新行的段落或完整内层表格行
     * @param height 原生测得的高度，单位为 pt
     */
    private record Atom(Element element, double height) {
    }

    private PdfNativeFragmenter() {
    }

    /**
     * 拆分标记为重复左侧内容的双列表格行。
     *
     * @param renderer 已完成原生布局的渲染器
     * @param original 本次布局使用的 XHTML
     * @return 分段后的 XHTML；无需分段时返回原文
     * @throws IOException 完整内容没有合法位置，或分段视图无法序列化
     */
    static String fragment(PdfBoxRenderer renderer, String original) throws IOException {
        // 以原生排版结果为准，先检查不可折行控件，再决定哪些行必须拆分。
        Map<Element, Box> boxes = new IdentityHashMap<>();
        List<TableRowBox> rows = new ArrayList<>();
        collect(renderer.getRootBox(), boxes, rows);
        validateShapeWidths(boxes);

        LayoutContext context = renderer.getSharedContext().newLayoutContextInstance();
        float dots = renderer.getDotsPerPoint();
        boolean changed = false;
        for (TableRowBox row : rows) {
            if (fragmentRow(renderer, row, boxes, context, dots)) {
                changed = true;
            }
        }
        if (!changed) {
            return original;
        }
        return serialize(renderer.getRootBox().getElement().getOwnerDocument());
    }

    /** 检查不可折行的向量控件是否超出最近一层单元格的内容宽度。 */
    private static void validateShapeWidths(Map<Element, Box> boxes) {
        for (Map.Entry<Element, Box> entry : boxes.entrySet()) {
            if (!"letool-shape".equals(entry.getKey().getAttribute("type"))) {
                continue;
            }
            for (Node ancestor = entry.getKey().getParentNode(); ancestor instanceof Element cell;
                    ancestor = ancestor.getParentNode()) {
                if ("td".equals(cell.getTagName()) || "th".equals(cell.getTagName())) {
                    Box container = boxes.get(cell);
                    if (container != null
                            && entry.getValue().getWidth() > container.getContentWidth() + LAYOUT_TOLERANCE_DOTS) {
                        throw PrintValidationException.invalidDocument("完整原生控件超过所属单元格可用宽度");
                    }
                    break;
                }
            }
        }
    }

    /** 仅在完整行超过整页容量时分段；普通可容纳行仍交给原生分页。 */
    private static boolean fragmentRow(PdfBoxRenderer renderer, TableRowBox row, Map<Element, Box> boxes,
            LayoutContext context, float dots) throws IOException {
        Element element = row.getElement();
        List<Element> cells = children(element);
        if (cells.size() != 2 || !"true".equals(cells.get(0).getAttribute("data-repeat-on-split"))
                || "true".equals(element.getAttribute("data-native-fragmented"))) {
            return false;
        }
        PageBox page = renderer.getRootBox().getLayer().getPage(context, row.getAbsY());
        double padding = Math.max(verticalPadding(boxes.get(cells.get(0)), context, dots),
                verticalPadding(boxes.get(cells.get(1)), context, dots)) + COLLAPSED_BORDER_ALLOWANCE;
        double capacity = contentCapacity(element, page, boxes, context, dots, padding);
        if (row.getHeight() / dots <= capacity + padding + HEIGHT_TOLERANCE) {
            return false;
        }
        double labelHeight = 0;
        for (Element child : children(cells.get(0))) {
            labelHeight += height(boxes.get(child), dots);
        }
        if (capacity <= 0 || labelHeight > capacity + HEIGHT_TOLERANCE) {
            throw new IOException("重复侧完整内容没有合法整页位置");
        }
        // 保留重复侧完整内容；右侧只在完整文字行或完整内层表格行之间分段。
        Element content = children(cells.get(1)).get(0);
        while ("section".equals(content.getTagName()) && children(content).size() == 1) {
            content = children(content).get(0);
        }
        List<Atom> atoms = atoms(content, boxes, dots, context);
        if (atoms.isEmpty()) {
            throw new IOException("超页行没有可分割的完整内容");
        }
        // 当前页放不下首块时整行从下一页开始，避免只留下重复侧的空片段。
        double firstCapacity = Math.min(capacity,
                (page.getBottom() - row.getAbsY() - row.getExtraSpaceBottom()) / dots - padding);
        if (firstCapacity < Math.max(labelHeight, atoms.get(0).height())) {
            firstCapacity = capacity;
        }
        replaceRow(element, content, chunks(atoms, firstCapacity, capacity));
        return true;
    }

    /** 扣除续页表头和外层单元格留白，避免分段后的行被再次挤出页面。 */
    private static double contentCapacity(Element row, PageBox page, Map<Element, Box> boxes,
            LayoutContext context, float dots, double padding) {
        double capacity = page.getContentHeight(context) / dots;
        Element table = (Element) row.getParentNode().getParentNode();
        for (Element child : children(table)) {
            if ("thead".equals(child.getTagName())) {
                capacity -= height(boxes.get(child), dots);
            }
        }
        capacity -= padding;
        for (Node ancestor = row.getParentNode(); ancestor instanceof Element container;
                ancestor = ancestor.getParentNode()) {
            if ("td".equals(container.getTagName()) || "th".equals(container.getTagName())) {
                capacity -= verticalPadding(boxes.get(container), context, dots);
            }
        }
        return capacity;
    }

    /** 按原顺序装入完整内容块；首片段使用当前页剩余空间，其后使用整页容量。 */
    private static List<List<Atom>> chunks(List<Atom> atoms, double firstCapacity, double capacity)
            throws IOException {
        List<List<Atom>> chunks = new ArrayList<>();
        List<Atom> chunk = new ArrayList<>();
        double used = 0;
        double limit = firstCapacity;
        for (Atom atom : atoms) {
            if (atom.height() > capacity + HEIGHT_TOLERANCE) {
                throw new IOException("完整内容块没有合法整页位置");
            }
            if (!chunk.isEmpty() && used + atom.height() > limit + CHUNK_TOLERANCE) {
                chunks.add(List.copyOf(chunk));
                chunk.clear();
                used = 0;
                limit = capacity;
            }
            chunk.add(atom);
            used += atom.height();
        }
        if (!chunk.isEmpty()) {
            chunks.add(List.copyOf(chunk));
        }
        return chunks;
    }

    /** 克隆原行的完整重复侧，并以每个片段的实际内容替换右侧。 */
    private static void replaceRow(Element row, Element content, List<List<Atom>> chunks) {
        Node parent = row.getParentNode();
        for (int index = 0; index < chunks.size(); index++) {
            Element replacement = (Element) row.cloneNode(true);
            replacement.setAttribute("data-native-fragmented", "true");
            // 链接目标保留在首片段；续页只复制内容，不能产生重复目标 ID。
            if (index > 0) {
                removeIds(replacement);
                replacement.setAttribute("style", replacement.getAttribute("style") + ";page-break-before:always;");
            }
            List<Element> replacementCells = children(replacement);
            replacementCells.get(0).removeAttribute("data-repeat-on-split");
            Element right = replacementCells.get(1);
            clearChildren(right);
            if ("table".equals(content.getTagName())) {
                Element copy = (Element) content.cloneNode(true);
                Element body = children(copy).stream()
                        .filter(child -> "tbody".equals(child.getTagName())).findFirst().orElseThrow();
                clearChildren(body);
                for (Atom atom : chunks.get(index)) {
                    body.appendChild(atom.element().cloneNode(true));
                }
                right.appendChild(copy);
            } else {
                for (Atom atom : chunks.get(index)) {
                    right.appendChild(atom.element().cloneNode(true));
                }
            }
            parent.insertBefore(replacement, row);
        }
        parent.removeChild(row);
    }

    /** 原地移除子节点；保留容器自身的样式、边框和属性。 */
    private static void clearChildren(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    /** 将已修改的 DOM 转回 XHTML，供下一轮原生布局使用。 */
    private static String serialize(Document document) throws IOException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (TransformerException exception) {
            throw new IOException("受控原生分段视图保存失败", exception);
        }
    }

    /** 内层表格以完整行为分段单位，普通段落以原生文字行为分段单位。 */
    private static List<Atom> atoms(Element content, Map<Element, Box> boxes, float dots,
            LayoutContext context) throws IOException {
        List<Atom> atoms = new ArrayList<>();
        if ("table".equals(content.getTagName())) {
            for (Element body : children(content)) {
                if ("tbody".equals(body.getTagName())) {
                    for (Element row : children(body)) {
                        atoms.add(new Atom(row, height(boxes.get(row), dots)));
                    }
                }
            }
        } else if ("p".equals(content.getTagName())) {
            collectParagraphAtoms(content, boxes.get(content), dots, context, atoms);
        } else {
            throw new IOException("重复行只允许按完整文本行或完整内层表格行分段");
        }
        return atoms;
    }

    /** 保留文字原顺序、空行高度和下划线标记，不重新按字符数估算换行。 */
    private static void collectParagraphAtoms(Element content, Box box, float dots, LayoutContext context,
            List<Atom> atoms) {
        List<LineBox> lines = new ArrayList<>();
        lines(box, lines);
        boolean underlined = false;
        for (int index = 0; index < content.getElementsByTagName("span").getLength(); index++) {
            Element span = (Element) content.getElementsByTagName("span").item(index);
            if ("true".equals(span.getAttribute("data-native-underline"))) {
                underlined = true;
            }
        }
        for (LineBox line : lines) {
            Element paragraph = (Element) content.cloneNode(false);
            paragraph.removeAttribute("id");
            String text = text(line);
            if (text.isEmpty()) {
                paragraph.setAttribute("style", "height:" + box.getStyle().getLineHeight(context) / dots + "pt;");
            } else {
                Element span = content.getOwnerDocument().createElement("span");
                if (underlined) {
                    span.setAttribute("data-native-underline", "true");
                }
                span.setTextContent(text);
                paragraph.appendChild(span);
            }
            atoms.add(new Atom(paragraph, box.getStyle().getLineHeight(context) / dots));
        }
    }

    /** 按布局树顺序提取当前行的实际文字，包含嵌套行内盒子。 */
    private static String text(Box box) {
        StringBuilder result = new StringBuilder();
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof InlineText value) {
                    result.append(value.getSubstring());
                } else if (child instanceof Box nested) {
                    result.append(text(nested));
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                result.append(text(child));
            }
        }
        return result.toString();
    }

    /** 收集原生文字行；找到行后不再向其内部重复遍历。 */
    private static void lines(Box box, List<LineBox> lines) {
        if (box == null) {
            return;
        }
        if (box instanceof LineBox line) {
            lines.add(line);
        } else {
            for (Box child : box.getChildren()) {
                lines(child, lines);
            }
        }
    }

    /** 将原生盒子高度换算为 pt；未产生布局盒子的节点不占高度。 */
    private static double height(Box box, float dots) {
        return box == null ? 0 : box.getHeight() / dots;
    }

    /** 返回单元格上下内边距之和，单位为 pt。 */
    private static double verticalPadding(Box box, LayoutContext context, float dots) {
        return box == null ? 0 : (box.getPadding(context).top() + box.getPadding(context).bottom()) / dots;
    }

    /** 获取直接子元素，忽略文本与注释节点。 */
    private static List<Element> children(Element element) {
        List<Element> result = new ArrayList<>();
        if (element != null) {
            for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element child) {
                    result.add(child);
                }
            }
        }
        return result;
    }

    /** 续页副本移除原目标 ID，确保链接仍指向首个实际出现位置。 */
    private static void removeIds(Element element) {
        element.removeAttribute("id");
        for (Element child : children(element)) {
            removeIds(child);
        }
    }

    /** 建立 DOM 与原生盒子的身份映射，同时收集需要检查的表格行。 */
    private static void collect(Box box, Map<Element, Box> boxes, List<TableRowBox> rows) {
        if (box.getElement() != null) {
            boxes.putIfAbsent(box.getElement(), box);
        }
        if (box instanceof TableRowBox row) {
            rows.add(row);
        }
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof Box nested) {
                    collect(nested, boxes, rows);
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                collect(child, boxes, rows);
            }
        }
    }
}
