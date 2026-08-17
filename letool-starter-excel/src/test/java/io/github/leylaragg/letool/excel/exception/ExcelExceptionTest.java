package io.github.leylaragg.letool.excel.exception;

import com.alibaba.excel.annotation.ExcelProperty;
import io.github.leylaragg.letool.excel.util.ExcelUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel 统一异常边界测试。
 */
@DisplayName("ExcelException 统一异常测试")
class ExcelExceptionTest {

    @Test
    @DisplayName("读取无效工作簿时应转换为稳定错误码并保留原因")
    void shouldWrapReadFailureWithStableErrorCode() {
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("sensitive-input-failure");
            }
        };

        assertThatThrownBy(() -> ExcelUtil.read(
                failingStream,
                TestRow.class
        )).isInstanceOfSatisfying(ExcelException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("EXCEL_001");
            assertThat(exception.getCause()).isNotNull();
            assertThat(exception.getMessage()).doesNotContain("sensitive-input-failure");
        });
    }

    @Test
    @DisplayName("写入失败时应转换为稳定错误码并保留原因")
    void shouldWrapWriteFailureWithStableErrorCode() {
        OutputStream failingStream = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("simulated-write-failure");
            }
        };

        assertThatThrownBy(() -> ExcelUtil.write(
                failingStream,
                "数据",
                List.of(new TestRow("value")),
                TestRow.class
        )).isInstanceOfSatisfying(ExcelException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("EXCEL_002");
            assertThat(exception.getCause()).isNotNull();
        });
    }

    @Test
    @DisplayName("空输入流应在调用 EasyExcel 前快速失败")
    void shouldRejectNullInputStream() {
        assertThatThrownBy(() -> ExcelUtil.read((InputStream) null, TestRow.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputStream");
    }

    @Test
    @DisplayName("空输出流应在调用 EasyExcel 前快速失败")
    void shouldRejectNullOutputStream() {
        assertThatThrownBy(() -> ExcelUtil.write(
                (OutputStream) null,
                "数据",
                List.of(),
                TestRow.class
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputStream");
    }

    /**
     * 异常路径测试实体。
     */
    public static class TestRow {

        @ExcelProperty("值")
        private String value;

        public TestRow() {
        }

        public TestRow(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
