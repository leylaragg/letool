package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.newtable.TableRowBox;
import com.openhtmltopdf.newtable.TableSectionBox;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.BlockBox;
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
import java.util.HashSet;
import java.util.Set;
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
     * @param containers 从内容单元格到本块的容器路径，用于逐片段重建结构
     */
    private record Atom(Element element, double height, List<Element> containers) {
    }

    private PdfNativeFragmenter() {
    }

    /**
     * 按唯一内容侧拆分表格行，完整复制其余声明重复的单元格。
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
            if ("true".equals(entry.getKey().getAttribute("data-keep-together"))) {
                Box parent = entry.getValue().getContainingBlock();
                if (entry.getValue().getWidth() > parent.getContentWidth() + LAYOUT_TOLERANCE_DOTS) {
                    throw PrintValidationException.invalidDocument("完整行内组合超过父容器可用宽度");
                }
            }
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
        int contentIndex = contentCellIndex(cells);
        if (contentIndex < 0 || "true".equals(element.getAttribute("data-native-fragmented"))) {
            return false;
        }
        PageBox page = renderer.getRootBox().getLayer().getPage(context, row.getAbsY());
        double padding = 0;
        for (Element cell : cells) {
            padding = Math.max(padding, verticalPadding(boxes.get(cell), context, dots));
        }
        padding += COLLAPSED_BORDER_ALLOWANCE;
        double capacity = contentCapacity(element, page, boxes, context, dots, padding);
        if (row.getHeight() / dots <= capacity + padding + HEIGHT_TOLERANCE) {
            return false;
        }
        double labelHeight = 0;
        for (int index = 0; index < cells.size(); index++) {
            if (index != contentIndex) {
                double repeatedHeight = 0;
                for (Element child : children(cells.get(index))) {
                    repeatedHeight += height(boxes.get(child), dots);
                }
                labelHeight = Math.max(labelHeight, repeatedHeight);
            }
        }
        if (capacity <= 0 || labelHeight > capacity + HEIGHT_TOLERANCE) {
            throw new IOException("重复侧完整内容没有合法整页位置");
        }
        // 保留重复侧完整内容；内容侧只在完整文字行或完整内层表格行之间分段。
        List<Atom> atoms = new ArrayList<>();
        for (Element content : children(cells.get(contentIndex))) {
            collectAtoms(content, List.of(), boxes, dots, context, atoms);
        }
        if (atoms.isEmpty()) {
            throw new IOException("超页行没有可分割的完整内容");
        }
        // 当前页放不下首块时整行从下一页开始，避免只留下重复侧的空片段。
        double firstCapacity = Math.min(capacity,
                (page.getBottom() - row.getAbsY() - row.getExtraSpaceBottom()) / dots - padding);
        if (firstCapacity < Math.max(labelHeight, atoms.get(0).height())) {
            firstCapacity = capacity;
        }
        replaceRow(element, contentIndex, chunks(atoms, firstCapacity, capacity));
        return true;
    }

    /** 至少一个重复侧和唯一可分段侧组成受支持的行，不改变普通表格的分页语义。 */
    private static int contentCellIndex(List<Element> cells) {
        if (cells.size() < 2) {
            return -1;
        }
        int contentIndex = -1;
        for (int index = 0; index < cells.size(); index++) {
            if (!"true".equals(cells.get(index).getAttribute("data-repeat-on-split"))) {
                if (contentIndex >= 0) {
                    return -1;
                }
                contentIndex = index;
            }
        }
        return contentIndex;
    }

    /** 扣除续页表头和外层单元格留白，避免分段后的行被再次挤出页面。 */
    private static double contentCapacity(Element row, PageBox page, Map<Element, Box> boxes,
            LayoutContext context, float dots, double padding) {
        double capacity = page.getContentHeight(context) / dots - padding;
        for (Node ancestor = row.getParentNode(); ancestor instanceof Element container;
                ancestor = ancestor.getParentNode()) {
            if ("table".equals(container.getTagName())) {
                for (Element section : children(container)) {
                    Box sectionBox = boxes.get(section);
                    if (sectionBox instanceof TableSectionBox header && header.isHeader()) {
                        capacity -= height(header, dots);
                    }
                }
            } else if ("td".equals(container.getTagName()) || "th".equals(container.getTagName())) {
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

    /** 克隆原行的完整重复侧，并以每个片段的实际内容替换唯一内容侧。 */
    private static void replaceRow(Element row, int contentIndex, List<List<Atom>> chunks) {
        Node parent = row.getParentNode();
        Set<String> retainedIds = new HashSet<>();
        for (int index = 0; index < chunks.size(); index++) {
            Element replacement = (Element) row.cloneNode(true);
            replacement.setAttribute("data-native-fragmented", "true");
            // 后续片段从新页开始；每个逻辑 ID 稍后仅在首次实际出现的位置保留。
            if (index > 0) {
                replacement.setAttribute("style", replacement.getAttribute("style") + ";page-break-before:always;");
            }
            List<Element> replacementCells = children(replacement);
            for (Element cell : replacementCells) {
                cell.removeAttribute("data-repeat-on-split");
            }
            Element contentCell = replacementCells.get(contentIndex);
            clearChildren(contentCell);
            Map<Element, Element> containers = new IdentityHashMap<>();
            for (Atom atom : chunks.get(index)) {
                appendAtom(contentCell, atom, containers);
            }
            retainFirstIds(replacement, retainedIds);
            parent.insertBefore(replacement, row);
        }
        parent.removeChild(row);
    }

    /** 按来源容器身份重建片段，混合段落和不同内表不会相互覆盖或串位。 */
    private static void appendAtom(Element cell, Atom atom, Map<Element, Element> containers) {
        Element target = cell;
        for (Element source : atom.containers()) {
            Element container = containers.get(source);
            if (container == null) {
                container = (Element) source.cloneNode(false);
                // 内表的列定义和表头属于每个完整片段；tbody 由后续原子按顺序填充。
                if ("table".equals(source.getTagName())) {
                    for (Element child : children(source)) {
                        if (!"tbody".equals(child.getTagName())) {
                            container.appendChild(child.cloneNode(true));
                        }
                    }
                }
                target.appendChild(container);
                containers.put(source, container);
            }
            target = container;
        }
        target.appendChild(atom.element().cloneNode(true));
    }

    /** 原地移除子节点；保留容器自身的样式、边框和属性。 */
    private static void clearChildren(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }

    /** 将已修改的 DOM 转回 XHTML，供下一轮原生布局使用。 */
    static String serialize(Document document) throws IOException {
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

    /** 按全部块的原顺序收集原子，容器只记录结构，不丢弃后续兄弟块。 */
    private static void collectAtoms(Element content, List<Element> containers, Map<Element, Box> boxes,
            float dots, LayoutContext context, List<Atom> atoms) {
        if ("section".equals(content.getTagName()) && !children(content).isEmpty()) {
            List<Element> nested = containerPath(containers, content);
            for (Element child : children(content)) {
                collectAtoms(child, nested, boxes, dots, context, atoms);
            }
        } else if ("table".equals(content.getTagName())) {
            int previousSize = atoms.size();
            List<Element> tablePath = containerPath(containers, content);
            for (Element body : children(content)) {
                if ("tbody".equals(body.getTagName())) {
                    List<Element> rowPath = containerPath(tablePath, body);
                    for (Element row : children(body)) {
                        atoms.add(new Atom(row, height(boxes.get(row), dots), rowPath));
                    }
                }
            }
            if (atoms.size() == previousSize) {
                // 只有表头的合法内表没有可拆正文行，仍作为完整块保留。
                atoms.add(new Atom(content, height(boxes.get(content), dots), containers));
            }
        } else if ("p".equals(content.getTagName())) {
            collectParagraphAtoms(content, boxes.get(content), dots, context, containers, atoms);
        } else {
            // 无内部拆分规则的块整体入队，超出整页容量时由 chunks 明确拒绝。
            atoms.add(new Atom(content, height(boxes.get(content), dots), containers));
        }
    }

    /** 冻结每个原子所属的容器路径，后续遍历不修改已生成原子的结构。 */
    private static List<Element> containerPath(List<Element> parents, Element container) {
        List<Element> result = new ArrayList<>(parents);
        result.add(container);
        return List.copyOf(result);
    }

    /** 原生行保留文本样式和替换对象，不能把含控件的行降为纯文本。 */
    private static void collectParagraphAtoms(Element content, Box box, float dots, LayoutContext context,
            List<Element> containers, List<Atom> atoms) {
        List<LineBox> lines = new ArrayList<>();
        lines(box, lines);
        if (lines.isEmpty()) {
            atoms.add(new Atom(content, height(box, dots), containers));
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            LineBox line = lines.get(index);
            Element paragraph = (Element) content.cloneNode(false);
            String style = content.getAttribute("style");
            if (index > 0) {
                paragraph.removeAttribute("id");
                style += ";margin-top:0;text-indent:0;";
            }
            if (index < lines.size() - 1) {
                style += ";margin-bottom:0;";
            }
            double lineHeight = Math.max(line.getHeight(), box.getStyle().getLineHeight(context)) / dots;
            boolean populated = appendLineContent(line, paragraph, content, content);
            if (!populated) {
                style += ";height:" + lineHeight + "pt;";
            }
            paragraph.setAttribute("style", style);
            double margin = index == 0 ? box.getMargin(context).top() / dots : 0;
            if (index == lines.size() - 1) {
                margin += box.getMargin(context).bottom() / dots;
            }
            atoms.add(new Atom(paragraph, lineHeight + margin, containers));
        }
    }

    /** 按实际行盒克隆行内层级；FreeText、方框等替换对象沿用原属性和完整尺寸。 */
    private static boolean appendLineContent(Box box, Element target, Element paragraph, Element parentSource) {
        Element source = box.getElement();
        // 行盒已代表一次断行，原始 br 不能再次增加片段的可见行数。
        if (source != null && "br".equals(source.getTagName())) {
            return false;
        }
        if (box instanceof BlockBox block && block.getReplacedElement() != null) {
            target.appendChild(source.cloneNode(true));
            return true;
        }
        Element destination = target;
        Element currentSource = parentSource;
        if (source != null && source != paragraph && source != parentSource) {
            destination = (Element) source.cloneNode(false);
            target.appendChild(destination);
            currentSource = source;
        }
        boolean populated = false;
        if (box instanceof InlineLayoutBox inline) {
            for (Object child : inline.getInlineChildren()) {
                if (child instanceof InlineText text) {
                    String visible = text.getSubstring().replace("\r", "").replace("\n", "");
                    if (!visible.isEmpty()) {
                        destination.appendChild(paragraph.getOwnerDocument().createTextNode(visible));
                        populated = true;
                    }
                } else if (child instanceof Box nested) {
                    populated |= appendLineContent(nested, destination, paragraph, currentSource);
                }
            }
        } else {
            for (Box child : box.getChildren()) {
                populated |= appendLineContent(child, destination, paragraph, currentSource);
            }
        }
        return populated;
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

    /** 每个逻辑目标仅保留在首次实际出现处，后面的独立块不会因位于续页而丢失目标。 */
    private static void retainFirstIds(Element element, Set<String> retainedIds) {
        String id = element.getAttribute("id");
        if (!id.isEmpty() && !retainedIds.add(id)) {
            element.removeAttribute("id");
        }
        for (Element child : children(element)) {
            retainFirstIds(child, retainedIds);
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
