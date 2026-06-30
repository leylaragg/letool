package com.github.leyland.letool.data.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DataProperties} 的单元测试 —— 验证配置属性类的默认值与 setter/getter 行为。
 */
@DisplayName("DataProperties 配置属性测试")
class DataPropertiesTest {

    // ======================== 顶层属性测试 ========================

    @Nested
    @DisplayName("顶层属性测试")
    class TopLevelPropertiesTests {

        @Test
        @DisplayName("enabled 默认应为 true")
        void enabledDefaultShouldBeTrue() {
            DataProperties props = new DataProperties();
            assertTrue(props.isEnabled(), "enabled 默认值应为 true");
        }

        @Test
        @DisplayName("enabled 可通过 setter 修改")
        void enabledShouldBeSettable() {
            DataProperties props = new DataProperties();
            props.setEnabled(false);
            assertFalse(props.isEnabled());
        }

        @Test
        @DisplayName("pagination 子配置不应为 null")
        void paginationShouldNotBeNull() {
            DataProperties props = new DataProperties();
            assertNotNull(props.getPagination(), "pagination 子配置不应为 null");
        }

        @Test
        @DisplayName("pagination 子配置可通过 setter 替换")
        void paginationShouldBeSettable() {
            DataProperties props = new DataProperties();
            DataProperties.Pagination newPagination = new DataProperties.Pagination();
            newPagination.setMaxPageSize(500);
            props.setPagination(newPagination);
            assertEquals(500, props.getPagination().getMaxPageSize());
        }

        @Test
        @DisplayName("mapping 子配置不应为 null")
        void mappingShouldNotBeNull() {
            DataProperties props = new DataProperties();
            assertNotNull(props.getMapping(), "mapping 子配置不应为 null");
        }

        @Test
        @DisplayName("mapping 子配置可通过 setter 替换")
        void mappingShouldBeSettable() {
            DataProperties props = new DataProperties();
            DataProperties.Mapping newMapping = new DataProperties.Mapping();
            newMapping.setAutoCamelCase(false);
            props.setMapping(newMapping);
            assertFalse(props.getMapping().isAutoCamelCase());
        }
    }

    // ======================== Pagination 子配置测试 ========================

    @Nested
    @DisplayName("Pagination 分页配置测试")
    class PaginationTests {

        @Test
        @DisplayName("maxPageSize 默认应为 1000")
        void maxPageSizeDefaultShouldBe1000() {
            DataProperties.Pagination pagination = new DataProperties.Pagination();
            assertEquals(1000, pagination.getMaxPageSize(), "maxPageSize 默认值应为 1000");
        }

        @Test
        @DisplayName("maxPageSize 可通过 setter 修改")
        void maxPageSizeShouldBeSettable() {
            DataProperties.Pagination pagination = new DataProperties.Pagination();
            pagination.setMaxPageSize(500);
            assertEquals(500, pagination.getMaxPageSize());
        }

        @Test
        @DisplayName("defaultPageSize 默认应为 20")
        void defaultPageSizeDefaultShouldBe20() {
            DataProperties.Pagination pagination = new DataProperties.Pagination();
            assertEquals(20, pagination.getDefaultPageSize(), "defaultPageSize 默认值应为 20");
        }

        @Test
        @DisplayName("defaultPageSize 可通过 setter 修改")
        void defaultPageSizeShouldBeSettable() {
            DataProperties.Pagination pagination = new DataProperties.Pagination();
            pagination.setDefaultPageSize(50);
            assertEquals(50, pagination.getDefaultPageSize());
        }
    }

    // ======================== Mapping 子配置测试 ========================

    @Nested
    @DisplayName("Mapping 映射配置测试")
    class MappingTests {

        @Test
        @DisplayName("autoCamelCase 默认应为 true")
        void autoCamelCaseDefaultShouldBeTrue() {
            DataProperties.Mapping mapping = new DataProperties.Mapping();
            assertTrue(mapping.isAutoCamelCase(), "autoCamelCase 默认值应为 true");
        }

        @Test
        @DisplayName("autoCamelCase 可通过 setter 修改")
        void autoCamelCaseShouldBeSettable() {
            DataProperties.Mapping mapping = new DataProperties.Mapping();
            mapping.setAutoCamelCase(false);
            assertFalse(mapping.isAutoCamelCase());
        }

        @Test
        @DisplayName("useGeneratedKeys 默认应为 true")
        void useGeneratedKeysDefaultShouldBeTrue() {
            DataProperties.Mapping mapping = new DataProperties.Mapping();
            assertTrue(mapping.isUseGeneratedKeys(), "useGeneratedKeys 默认值应为 true");
        }

        @Test
        @DisplayName("useGeneratedKeys 可通过 setter 修改")
        void useGeneratedKeysShouldBeSettable() {
            DataProperties.Mapping mapping = new DataProperties.Mapping();
            mapping.setUseGeneratedKeys(false);
            assertFalse(mapping.isUseGeneratedKeys());
        }
    }

    // ======================== 整合测试 ========================

    @Nested
    @DisplayName("整合测试")
    class IntegrationTests {

        @Test
        @DisplayName("各级配置默认值应组成完整的默认配置")
        void allDefaultsShouldFormCompleteConfiguration() {
            DataProperties props = new DataProperties();

            assertTrue(props.isEnabled());
            assertNotNull(props.getPagination());
            assertEquals(1000, props.getPagination().getMaxPageSize());
            assertEquals(20, props.getPagination().getDefaultPageSize());
            assertNotNull(props.getMapping());
            assertTrue(props.getMapping().isAutoCamelCase());
            assertTrue(props.getMapping().isUseGeneratedKeys());
        }
    }
}
