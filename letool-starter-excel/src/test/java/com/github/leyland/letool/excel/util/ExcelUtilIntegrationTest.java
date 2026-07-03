package com.github.leyland.letool.excel.util;

import com.github.leyland.letool.excel.annotation.ExcelColumn;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real XLSX integration tests for {@link ExcelUtil}.
 */
class ExcelUtilIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that letool's {@link ExcelColumn} controls header names, order, and read mapping.
     */
    @Test
    void shouldWriteAndReadUsingExcelColumnMetadata() throws Exception {
        Path output = tempDir.resolve("users.xlsx");
        List<UserRow> rows = List.of(
                new UserRow("Alice", 18),
                new UserRow("Bob", 21));

        ExcelUtil.write(output.toString(), "Users", rows, UserRow.class);

        try (Workbook workbook = WorkbookFactory.create(output.toFile())) {
            assertThat(workbook.getSheet("Users").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Age");
            assertThat(workbook.getSheet("Users").getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("User Name");
        }

        List<UserRow> actual = ExcelUtil.read(output.toString(), UserRow.class);

        assertThat(actual)
                .extracting(UserRow::getName)
                .containsExactly("Alice", "Bob");
        assertThat(actual)
                .extracting(UserRow::getAge)
                .containsExactly(18, 21);
    }

    /**
     * Verifies stream-based write/read with the same custom mapping path.
     */
    @Test
    void shouldWriteAndReadStreamUsingExcelColumnMetadata() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExcelUtil.write(outputStream, "Users", List.of(new UserRow("Stream", 30)), UserRow.class);

        List<UserRow> actual = ExcelUtil.read(
                new ByteArrayInputStream(outputStream.toByteArray()),
                UserRow.class);

        assertThat(actual).singleElement().satisfies(row -> {
            assertThat(row.getName()).isEqualTo("Stream");
            assertThat(row.getAge()).isEqualTo(30);
        });
    }

    /**
     * Test entity with intentionally reversed declaration/index order.
     */
    static class UserRow {

        @ExcelColumn(value = "User Name", index = 1)
        private String name;

        @ExcelColumn(value = "Age", index = 0)
        private Integer age;

        UserRow() {
        }

        UserRow(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        String getName() {
            return name;
        }

        Integer getAge() {
            return age;
        }
    }
}
