package com.github.leyland.letool.excel.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationResult Excel 校验结果测试")
class ValidationResultTest {

    @Nested
    @DisplayName("基本操作测试")
    class BasicTests {

        @Test
        @DisplayName("新实例应无错误")
        void newInstanceShouldHaveNoErrors() {
            ValidationResult result = new ValidationResult();
            assertFalse(result.hasErrors());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("添加错误后 hasErrors 应返回 true")
        void shouldHaveErrorsAfterAdding() {
            ValidationResult result = new ValidationResult();
            result.addError(3, "username", "用户名不能为空");

            assertTrue(result.hasErrors());
            assertEquals(1, result.getErrors().size());
        }

        @Test
        @DisplayName("添加多条错误应正确记录")
        void shouldRecordMultipleErrors() {
            ValidationResult result = new ValidationResult();
            result.addError(2, "name", "不能为空");
            result.addError(5, "phone", "格式不正确");
            result.addError(7, "email", "不能为空");

            assertEquals(3, result.getErrors().size());
        }
    }

    @Nested
    @DisplayName("ValidationError 单条错误测试")
    class ValidationErrorTests {

        @Test
        @DisplayName("应正确记录行号、字段名和错误消息")
        void shouldRecordRowFieldAndMessage() {
            ValidationResult.ValidationError error =
                    new ValidationResult.ValidationError(5, "phone", "格式不正确");

            assertEquals(5, error.getRow());
            assertEquals("phone", error.getField());
            assertEquals("格式不正确", error.getMessage());
        }

        @Test
        @DisplayName("不同错误的属性应独立")
        void differentErrorsShouldBeIndependent() {
            ValidationResult.ValidationError e1 =
                    new ValidationResult.ValidationError(1, "a", "msgA");
            ValidationResult.ValidationError e2 =
                    new ValidationResult.ValidationError(2, "b", "msgB");

            assertEquals(1, e1.getRow());
            assertEquals("a", e1.getField());
            assertEquals("msgA", e1.getMessage());

            assertEquals(2, e2.getRow());
            assertEquals("b", e2.getField());
            assertEquals("msgB", e2.getMessage());
        }
    }

    @Nested
    @DisplayName("getErrors() 测试")
    class GetErrorsTests {

        @Test
        @DisplayName("getErrors 应按添加顺序返回")
        void getErrorsShouldReturnInOrder() {
            ValidationResult result = new ValidationResult();
            result.addError(1, "a", "m1");
            result.addError(2, "b", "m2");
            result.addError(3, "c", "m3");

            List<ValidationResult.ValidationError> errors = result.getErrors();
            assertEquals(1, errors.get(0).getRow());
            assertEquals(2, errors.get(1).getRow());
            assertEquals(3, errors.get(2).getRow());
        }

        @Test
        @DisplayName("addError 返回的行号与传入一致")
        void addErrorShouldPreserveRowNumber() {
            ValidationResult result = new ValidationResult();
            result.addError(10, "field", "msg");

            assertEquals(10, result.getErrors().get(0).getRow());
        }
    }
}
