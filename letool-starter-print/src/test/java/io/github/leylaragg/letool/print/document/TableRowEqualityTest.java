package io.github.leylaragg.letool.print.document;

import io.github.leylaragg.letool.print.document.node.TableCell;
import io.github.leylaragg.letool.print.document.node.TableRow;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 表格行用作集合元素时，排版约束也应参与值比较。 */
class TableRowEqualityTest {

    @Test
    void shouldKeepRowsWithDifferentLayoutConstraintsDistinct() {
        List<TableCell> cells = List.of(new TableCell(List.of(), 1, 1));
        TableRow natural = new TableRow(cells);
        TableRow minimumHeight = new TableRow(cells, 24, false);
        TableRow keepWithNext = new TableRow(cells, 0, true);

        assertThat(natural).isNotEqualTo(minimumHeight).isNotEqualTo(keepWithNext);
        Set<TableRow> rows = new HashSet<>(List.of(natural, minimumHeight, keepWithNext));
        assertThat(rows).hasSize(3);
    }

    @Test
    void shouldFindEquivalentRowsInAHashSet() {
        TableRow first = new TableRow(List.of(new TableCell(List.of(), 1, 1)), 24, true);
        TableRow same = new TableRow(List.of(new TableCell(List.of(), 1, 1)), 24, true);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(new HashSet<>(List.of(first))).contains(same);
    }
}
