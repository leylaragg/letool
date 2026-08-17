package com.github.leyland.letool.print.docx;

import com.github.leyland.letool.print.document.node.TableCell;
import com.github.leyland.letool.print.document.node.TableNode;
import com.github.leyland.letool.print.document.node.TableRow;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblGrid;
import org.docx4j.wml.TblGridCol;
import org.docx4j.wml.Tc;
import org.docx4j.wml.TcPr;
import org.docx4j.wml.TcPrInner;
import org.docx4j.wml.Tr;
import org.docx4j.wml.TrPr;

import java.math.BigInteger;
import java.util.List;

/**
 * 把已校验的严格网格写成 Word 表格和合并单元格。
 *
 * @author leyland
 */
final class DocxTableWriter {

    /** 默认网格列宽只用于建立稳定结构，编辑器仍可按页面自动调整。 */
    private static final BigInteger DEFAULT_COLUMN_WIDTH = BigInteger.valueOf(2_000);

    /**
     * 写入一个完整表格。
     *
     * @param context 当前渲染上下文
     * @param table 已通过核心网格校验的表格
     * @param bodyWriter 单元格内容沿用的块节点写入器
     * @param anchorNames 表格和外层章节需要落到首段的书签名称
     * @return Word 表格
     */
    Tbl write(
            DocxRenderContext context,
            TableNode table,
            DocxBodyWriter bodyWriter,
            List<String> anchorNames) {
        int columns = table.rows().get(0).cells().stream()
                .mapToInt(TableCell::colSpan).sum();
        Tbl result = context.factory().createTbl();
        result.setTblGrid(grid(context, columns));

        MergeSpan[] activeMerges = new MergeSpan[columns];
        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            Tr row = writeRow(
                    context, table.rows().get(rowIndex), activeMerges, bodyWriter);
            if (rowIndex < table.headerRowCount()) {
                markRepeatingHeader(context, row);
            }
            result.getContent().add(row);
        }
        new DocxNavigationWriter().anchorFirstParagraph(context, result, anchorNames);
        return result;
    }

    /** 创建固定列数的 Word 网格。 */
    private TblGrid grid(DocxRenderContext context, int columns) {
        TblGrid grid = context.factory().createTblGrid();
        for (int column = 0; column < columns; column++) {
            TblGridCol gridColumn = context.factory().createTblGridCol();
            gridColumn.setW(DEFAULT_COLUMN_WIDTH);
            grid.getGridCol().add(gridColumn);
        }
        return grid;
    }

    /** 写入声明单元格，并在被跨行占据的位置补上继续单元格。 */
    private Tr writeRow(
            DocxRenderContext context,
            TableRow source,
            MergeSpan[] activeMerges,
            DocxBodyWriter bodyWriter) {
        Tr row = context.factory().createTr();
        int sourceIndex = 0;
        int column = 0;
        while (column < activeMerges.length) {
            MergeSpan active = activeMerges[column];
            if (active != null) {
                row.getContent().add(cell(context, null, active.width, "continue", bodyWriter));
                finishContinuation(activeMerges, active);
                column += active.width;
                continue;
            }

            TableCell sourceCell = source.cells().get(sourceIndex++);
            String verticalMerge = sourceCell.rowSpan() > 1 ? "restart" : null;
            row.getContent().add(cell(
                    context, sourceCell, sourceCell.colSpan(), verticalMerge, bodyWriter));
            if (sourceCell.rowSpan() > 1) {
                MergeSpan merge = new MergeSpan(column, sourceCell.colSpan(), sourceCell.rowSpan() - 1);
                for (int occupied = column; occupied < column + sourceCell.colSpan(); occupied++) {
                    activeMerges[occupied] = merge;
                }
            }
            column += sourceCell.colSpan();
        }
        return row;
    }

    /** 当前行消费一次跨行续接，最后一次消费后释放对应网格位置。 */
    private void finishContinuation(MergeSpan[] activeMerges, MergeSpan merge) {
        merge.remainingRows--;
        if (merge.remainingRows > 0) {
            return;
        }
        for (int column = merge.startColumn; column < merge.startColumn + merge.width; column++) {
            activeMerges[column] = null;
        }
    }

    /** 创建普通、跨列或跨行单元格，并保证其中至少有一个段落。 */
    private Tc cell(
            DocxRenderContext context,
            TableCell source,
            int columnSpan,
            String verticalMerge,
            DocxBodyWriter bodyWriter) {
        Tc cell = context.factory().createTc();
        TcPr properties = context.factory().createTcPr();
        if (columnSpan > 1) {
            TcPrInner.GridSpan gridSpan = context.factory().createTcPrInnerGridSpan();
            gridSpan.setVal(BigInteger.valueOf(columnSpan));
            properties.setGridSpan(gridSpan);
        }
        if (verticalMerge != null) {
            TcPrInner.VMerge merge = context.factory().createTcPrInnerVMerge();
            merge.setVal(verticalMerge);
            properties.setVMerge(merge);
        }
        cell.setTcPr(properties);
        if (source != null) {
            bodyWriter.writeInto(context, cell.getContent(), source.content());
        }
        if (cell.getContent().isEmpty()) {
            cell.getContent().add(context.factory().createP());
        }
        return cell;
    }

    /** 标记表头行，让 Word 在分页后自动重复显示。 */
    private void markRepeatingHeader(DocxRenderContext context, Tr row) {
        TrPr properties = context.factory().createTrPr();
        properties.getCnfStyleOrDivIdOrGridBefore().add(
                context.factory().createCTTrPrBaseTblHeader(new BooleanDefaultTrue()));
        row.setTrPr(properties);
    }

    /** 保存一个跨行单元格在后续行中的占位状态。 */
    private static final class MergeSpan {
        private final int startColumn;
        private final int width;
        private int remainingRows;

        /** 创建跨行占位。 */
        private MergeSpan(int startColumn, int width, int remainingRows) {
            this.startColumn = startColumn;
            this.width = width;
            this.remainingRows = remainingRows;
        }
    }
}
