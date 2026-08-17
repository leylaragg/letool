package io.github.leylaragg.letool.excel.util;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import io.github.leylaragg.letool.excel.annotation.ExcelValidation;
import io.github.leylaragg.letool.excel.validation.ValidationResult;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExcelUtil} 真实 XLSX 集成测试。
 */
class ExcelUtilIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * 验证 EasyExcel 原生注解控制表头、顺序和读取映射。
     */
    @Test
    void shouldWriteAndReadUsingNativeEasyExcelMetadata() throws Exception {
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
     * 验证流式读写使用相同的 EasyExcel 原生映射。
     */
    @Test
    void shouldWriteAndReadStreamUsingNativeMetadata() {
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
     * 验证 EasyExcel 原生日期格式和转换器可通过薄封装正常工作。
     */
    @Test
    void shouldHonorNativeDateFormatAndConverter() {
        Path output = tempDir.resolve("native-features.xlsx");
        NativeFeatureRow row = new NativeFeatureRow(
                LocalDate.of(2026, 7, 30),
                Status.ENABLED
        );

        ExcelUtil.write(output.toString(), "Native", List.of(row), NativeFeatureRow.class);
        List<NativeFeatureRow> actual = ExcelUtil.read(output.toString(), NativeFeatureRow.class);

        assertThat(actual).singleElement().satisfies(value -> {
            assertThat(value.getBirthday()).isEqualTo(LocalDate.of(2026, 7, 30));
            assertThat(value.getStatus()).isEqualTo(Status.ENABLED);
        });
    }

    /**
     * 验证调用方传入的输入流和输出流不会被工具类关闭。
     */
    @Test
    void shouldKeepCallerOwnedStreamsOpen() {
        TrackingOutputStream outputStream = new TrackingOutputStream();

        ExcelUtil.write(outputStream, "Users", List.of(new UserRow("Stream", 30)), UserRow.class);

        assertThat(outputStream.isClosed()).isFalse();
        TrackingInputStream inputStream = new TrackingInputStream(outputStream.toByteArray());

        List<UserRow> actual = ExcelUtil.read(inputStream, UserRow.class);

        assertThat(actual).hasSize(1);
        assertThat(inputStream.isClosed()).isFalse();
    }

    /**
     * 验证大数据读取按照指定批次交付，且最后不足一批的数据不会丢失。
     */
    @Test
    void shouldReadInConfiguredBatchSizes() {
        Path output = tempDir.resolve("batch.xlsx");
        List<UserRow> rows = List.of(
                new UserRow("A", 1),
                new UserRow("B", 2),
                new UserRow("C", 3),
                new UserRow("D", 4),
                new UserRow("E", 5)
        );
        ExcelUtil.write(output.toString(), "Batch", rows, UserRow.class);
        List<List<UserRow>> batches = new ArrayList<>();

        ExcelUtil.batchRead(output.toString(), UserRow.class, 2, batches::add);

        assertThat(batches).extracting(List::size).containsExactly(2, 2, 1);
        assertThat(batches)
                .flatExtracting(batch -> batch)
                .extracting(UserRow::getName)
                .containsExactly("A", "B", "C", "D", "E");
        assertThatThrownBy(() -> batches.get(0).add(new UserRow("F", 6)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证无效批次大小在读取文件前快速失败。
     */
    @Test
    void shouldRejectNonPositiveBatchSize() {
        assertThatThrownBy(() ->
                ExcelUtil.batchRead("not-read.xlsx", UserRow.class, 0, rows -> {
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    /**
     * 验证批次回调自身的运行时异常不会被包装成 Excel 读取异常。
     */
    @Test
    void shouldPropagateBatchConsumerFailure() {
        Path output = tempDir.resolve("batch-consumer-failure.xlsx");
        ExcelUtil.write(
                output.toString(),
                "Batch",
                List.of(new UserRow("A", 1)),
                UserRow.class
        );
        IllegalStateException consumerFailure =
                new IllegalStateException("批次处理失败");

        assertThatThrownBy(() -> ExcelUtil.batchRead(
                output.toString(),
                UserRow.class,
                1,
                batch -> {
                    throw consumerFailure;
                }
        )).isSameAs(consumerFailure);
    }

    /**
     * 验证读取校验会累计行数，并使用工作簿中的真实数据行号。
     */
    @Test
    void shouldReadAndValidateUsingActualExcelRowNumber() {
        Path output = tempDir.resolve("validation.xlsx");
        List<ValidationRow> rows = List.of(
                new ValidationRow("有效名称"),
                new ValidationRow("短")
        );
        ExcelUtil.write(output.toString(), "校验", rows, ValidationRow.class);

        ValidationResult result = ExcelUtil.readAndValidate(
                output.toString(),
                ValidationRow.class
        );

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRow()).isEqualTo(3);
            assertThat(error.getField()).isEqualTo("name");
            assertThat(error.getMessage()).isEqualTo("名称至少需要两个字符");
        });
    }

    /**
     * 使用相反字段声明顺序和列索引的测试实体。
     */
    public static class UserRow {

        @ExcelProperty(value = "User Name", index = 1)
        private String name;

        @ExcelProperty(value = "Age", index = 0)
        private Integer age;

        public UserRow() {
        }

        public UserRow(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    /**
     * 验证 EasyExcel 原生日期和转换器能力的测试实体。
     */
    public static class NativeFeatureRow {

        @ExcelProperty("出生日期")
        @DateTimeFormat("yyyy-MM-dd")
        private LocalDate birthday;

        @ExcelProperty(value = "状态", converter = StatusConverter.class)
        private Status status;

        public NativeFeatureRow() {
        }

        public NativeFeatureRow(LocalDate birthday, Status status) {
            this.birthday = birthday;
            this.status = status;
        }

        public LocalDate getBirthday() {
            return birthday;
        }

        public void setBirthday(LocalDate birthday) {
            this.birthday = birthday;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }

    /**
     * 用于验证读取期间数据校验的测试实体。
     */
    public static class ValidationRow {

        @ExcelProperty("名称")
        @ExcelValidation(minLength = 2, message = "名称至少需要两个字符")
        private String name;

        public ValidationRow() {
        }

        public ValidationRow(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 测试状态枚举。
     */
    enum Status {
        ENABLED,
        DISABLED
    }

    /**
     * 供 EasyExcel 原生注解直接引用的状态转换器。
     */
    public static class StatusConverter implements Converter<Status> {

        @Override
        public Class<?> supportJavaTypeKey() {
            return Status.class;
        }

        @Override
        public CellDataTypeEnum supportExcelTypeKey() {
            return CellDataTypeEnum.STRING;
        }

        @Override
        public Status convertToJavaData(
                ReadCellData<?> cellData,
                ExcelContentProperty contentProperty,
                GlobalConfiguration globalConfiguration) {
            return Status.valueOf(cellData.getStringValue().toUpperCase());
        }

        @Override
        public WriteCellData<?> convertToExcelData(
                Status value,
                ExcelContentProperty contentProperty,
                GlobalConfiguration globalConfiguration) {
            return new WriteCellData<>(value.name().toLowerCase());
        }
    }

    /**
     * 记录关闭状态的测试输出流。
     */
    private static class TrackingOutputStream extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }

    /**
     * 记录关闭状态的测试输入流。
     */
    private static class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }
}
