package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.api.RenderOptions;
import com.github.leyland.letool.print.document.DocumentMetadata;
import com.github.leyland.letool.print.document.DocumentModel;
import com.github.leyland.letool.print.document.PageLayout;
import com.github.leyland.letool.print.document.node.ParagraphNode;
import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import com.github.leyland.letool.print.document.node.TextNode;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Tbl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证通用严格网格能够稳定映射为 Word 表格。
 *
 * @author leyland
 */
class DocxTableWriterTest {

    /** 跨行、跨列和重复表头都应保留在 OOXML 表格属性中。 */
    @Test
    void shouldWriteMergedGridAndRepeatingHeader() throws Exception {
        TableNode table = new TableNode("summary", 2, List.of(
                row(cell("表头纵向", 2, 1), cell("表头横向", 1, 2)),
                row(cell("第二行表头", 1, 2)),
                row(cell("正文一", 1, 1), cell("正文二", 1, 2))));
        DocumentModel document = new DocumentModel(
                DocumentMetadata.empty(), PageLayout.a4Portrait(), List.of(table));

        byte[] content = new DocxDocumentRenderer(DocxRendererOptions.defaults())
                .render(document, RenderOptions.defaults()).content();
        WordprocessingMLPackage reopened = WordprocessingMLPackage.load(
                new ByteArrayInputStream(content));
        Tbl result = (Tbl) XmlUtils.unwrap(
                reopened.getMainDocumentPart().getContent().get(0));
        String tableXml = XmlUtils.marshaltoString(result, true, true);

        assertThat(result.getTblGrid().getGridCol()).hasSize(3);
        assertThat(tableXml)
                .contains("w:gridSpan")
                .contains("w:vMerge w:val=\"restart\"")
                .contains("w:vMerge w:val=\"continue\"")
                .contains("w:tblHeader")
                .contains("表头纵向", "第二行表头", "正文二");
    }

    /** 创建带一个普通段落的表格单元格。 */
    private static TableCell cell(String text, int rowSpan, int colSpan) {
        return new TableCell(List.of(
                new ParagraphNode("", List.of(new TextNode(text)))), rowSpan, colSpan);
    }

    /** 创建一行表格模型。 */
    private static TableRow row(TableCell... cells) {
        return new TableRow(List.of(cells));
    }
}
