package io.github.leylaragg.letool.excel.validation;

import io.github.leylaragg.letool.excel.annotation.ExcelValidation;
import io.github.leylaragg.letool.excel.exception.ExcelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel 行数据校验器测试。
 */
@DisplayName("DataValidator 数据校验测试")
class DataValidatorTest {

    @Test
    @DisplayName("必填字段仅包含空白字符时应校验失败")
    void shouldRejectBlankRequiredValue() {
        ValidationRow row = new ValidationRow();
        row.name = "   ";
        row.code = "ABC";

        ValidationResult result = DataValidator.validate(row, 2);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .filteredOn(error -> error.getField().equals("name"))
                .singleElement()
                .extracting(ValidationResult.ValidationError::getMessage)
                .isEqualTo("名称不能为空");
    }

    @Test
    @DisplayName("长度校验失败时应使用注解中的自定义消息")
    void shouldUseCustomMessageForLengthFailure() {
        ValidationRow row = new ValidationRow();
        row.name = "张三";
        row.code = "A";

        ValidationResult result = DataValidator.validate(row, 3);

        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getMessage)
                .contains("编码长度不正确");
    }

    @Test
    @DisplayName("应校验父类中声明的字段")
    void shouldValidateInheritedField() {
        ValidationRow row = new ValidationRow();
        row.name = "张三";
        row.code = "ABC";

        ValidationResult result = DataValidator.validate(row, 4);

        assertThat(result.getErrors())
                .extracting(ValidationResult.ValidationError::getField)
                .contains("tenantId");
    }

    @Test
    @DisplayName("空实体应快速失败")
    void shouldRejectNullEntity() {
        assertThatThrownBy(() -> DataValidator.validate(null, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity");
    }

    @Test
    @DisplayName("小于一的行号应快速失败")
    void shouldRejectInvalidRowNumber() {
        assertThatThrownBy(() -> DataValidator.validate(new ValidationRow(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rowNum");
    }

    @Test
    @DisplayName("校验规则执行失败时应转换为统一异常")
    void shouldWrapValidationInfrastructureFailure() {
        assertThatThrownBy(() -> DataValidator.validate(new InvalidRegexRow(), 2))
                .isInstanceOfSatisfying(ExcelException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("EXCEL_003");
                    assertThat(exception.getCause()).isNotNull();
                });
    }

    /**
     * 包含父类校验字段的测试实体。
     */
    private static class ValidationRow extends BaseRow {

        @ExcelValidation(required = true, minLength = 4, message = "名称不能为空")
        private String name;

        @ExcelValidation(minLength = 3, maxLength = 3, message = "编码长度不正确")
        private String code;
    }

    /**
     * 用于验证继承字段扫描的测试父类。
     */
    private static class BaseRow {

        @ExcelValidation(required = true, message = "租户不能为空")
        private String tenantId;
    }

    /**
     * 包含无效正则表达式的测试实体。
     */
    private static class InvalidRegexRow {

        @ExcelValidation(regex = "[")
        private String value = "value";
    }
}
