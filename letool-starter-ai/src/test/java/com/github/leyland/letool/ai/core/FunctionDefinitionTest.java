package com.github.leyland.letool.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FunctionDefinition 函数定义测试")
class FunctionDefinitionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("(name, description) 构造初始化 parameters 结构")
        void constructorNameDescription() {
            FunctionDefinition def = new FunctionDefinition("get_weather", "获取天气");

            assertEquals("get_weather", def.getName());
            assertEquals("获取天气", def.getDescription());

            Map<String, Object> params = def.getParameters();
            assertNotNull(params);
            assertEquals("object", params.get("type"));
            assertNotNull(params.get("properties"));
            assertNotNull(params.get("required"));
        }

        @Test
        @DisplayName("默认构造函数")
        void defaultConstructor() {
            FunctionDefinition def = new FunctionDefinition();
            assertNull(def.getName());
            assertNull(def.getDescription());
            assertNull(def.getParameters());
        }
    }

    @Nested
    @DisplayName("Builder 构建器")
    class BuilderTests {

        @Test
        @DisplayName("基本构建（仅 name + description）")
        void builderBasic() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("search")
                    .description("搜索信息")
                    .build();

            assertEquals("search", def.getName());
            assertEquals("搜索信息", def.getDescription());

            Map<String, Object> params = def.getParameters();
            assertEquals("object", params.get("type"));
            assertTrue(((Map<?, ?>) params.get("properties")).isEmpty());
            assertTrue(((java.util.List<?>) params.get("required")).isEmpty());
        }

        @Test
        @DisplayName("addParameter 添加可选参数")
        void addParameter() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("get_weather")
                    .description("获取天气")
                    .addParameter("city", "string", "城市名")
                    .addParameter("date", "string", "日期")
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) def.getParameters().get("properties");
            assertEquals(2, properties.size());

            @SuppressWarnings("unchecked")
            Map<String, Object> cityDef = (Map<String, Object>) properties.get("city");
            assertEquals("string", cityDef.get("type"));
            assertEquals("城市名", cityDef.get("description"));

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) def.getParameters().get("required");
            assertTrue(required.isEmpty());
        }

        @Test
        @DisplayName("addRequiredParameter 添加必填参数")
        void addRequiredParameter() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("get_weather")
                    .description("获取天气")
                    .addRequiredParameter("city", "string", "城市名")
                    .build();

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) def.getParameters().get("required");
            assertEquals(1, required.size());
            assertTrue(required.contains("city"));
        }

        @Test
        @DisplayName("混合可选和必填参数")
        void mixedParameters() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("search")
                    .description("搜索")
                    .addRequiredParameter("query", "string", "搜索关键词")
                    .addParameter("limit", "integer", "返回数量")
                    .build();

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) def.getParameters().get("required");
            assertEquals(1, required.size());
            assertEquals("query", required.get(0));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) def.getParameters().get("properties");
            assertEquals(2, properties.size());
            assertTrue(properties.containsKey("query"));
            assertTrue(properties.containsKey("limit"));
        }

        @Test
        @DisplayName("required(String...) 设置所有必填参数")
        void requiredMethod() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("func")
                    .description("desc")
                    .addParameter("a", "string", "A")
                    .addParameter("b", "string", "B")
                    .addParameter("c", "string", "C")
                    .required("a", "c")
                    .build();

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) def.getParameters().get("required");
            assertEquals(2, required.size());
            assertTrue(required.contains("a"));
            assertTrue(required.contains("c"));
            assertFalse(required.contains("b"));
        }

        @Test
        @DisplayName("required() 覆盖之前通过 addRequiredParameter 设置的必填项")
        void requiredOverrides() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("func")
                    .description("desc")
                    .addRequiredParameter("old", "string", "old required")
                    .required("new_field")
                    .build();

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) def.getParameters().get("required");
            assertEquals(1, required.size());
            assertEquals("new_field", required.get(0));
        }
    }

    @Nested
    @DisplayName("getter / setter")
    class GetterSetterTests {

        @Test
        @DisplayName("setName / setName")
        void nameGetterSetter() {
            FunctionDefinition def = new FunctionDefinition();
            def.setName("test_func");
            assertEquals("test_func", def.getName());
        }

        @Test
        @DisplayName("getDescription / setDescription")
        void descriptionGetterSetter() {
            FunctionDefinition def = new FunctionDefinition();
            def.setDescription("测试函数");
            assertEquals("测试函数", def.getDescription());
        }

        @Test
        @DisplayName("getParameters / setParameters")
        void parametersGetterSetter() {
            FunctionDefinition def = new FunctionDefinition();
            Map<String, Object> customParams = Map.of("type", "object");
            def.setParameters(customParams);
            assertSame(customParams, def.getParameters());
        }
    }

    @Nested
    @DisplayName("parameters JSON Schema 结构")
    class ParametersSchemaTests {

        @Test
        @DisplayName("完整 JSON Schema 结构")
        void completeSchemaStructure() {
            FunctionDefinition def = FunctionDefinition.builder()
                    .name("order_pizza")
                    .description("订购披萨")
                    .addRequiredParameter("size", "string", "尺寸：小/中/大")
                    .addParameter("toppings", "array", "配料列表")
                    .addParameter("quantity", "integer", "数量")
                    .build();

            Map<String, Object> params = def.getParameters();
            assertEquals("object", params.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) params.get("properties");
            assertEquals(3, props.size());
            assertTrue(props.containsKey("size"));
            assertTrue(props.containsKey("toppings"));
            assertTrue(props.containsKey("quantity"));

            @SuppressWarnings("unchecked")
            Map<String, Object> sizeDef = (Map<String, Object>) props.get("size");
            assertEquals("string", sizeDef.get("type"));
            assertEquals("尺寸：小/中/大", sizeDef.get("description"));

            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) params.get("required");
            assertEquals(1, required.size());
            assertEquals("size", required.get(0));
        }
    }
}
