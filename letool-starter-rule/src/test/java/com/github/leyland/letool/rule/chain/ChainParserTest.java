package com.github.leyland.letool.rule.chain;

import com.github.leyland.letool.rule.exception.RuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChainParser 测试")
class ChainParserTest {

    private final ChainParser parser = new ChainParser();

    @Nested
    @DisplayName("parseYaml 方法")
    class ParseYamlTests {

        @Test
        @DisplayName("应正确解析简单 YAML 规则链定义")
        void shouldParseSimpleYaml() {
            String yaml = "name: risk-evaluation\n" +
                    "description: 风险评估规则链\n" +
                    "nodes:\n" +
                    "  - name: dataCollector\n" +
                    "    type: THEN\n" +
                    "  - name: scoreCalculator\n" +
                    "    type: THEN";

            ChainDefinition chain = parser.parseYaml(yaml);

            assertEquals("risk-evaluation", chain.getName());
            assertEquals("风险评估规则链", chain.getDescription());
            assertEquals(2, chain.getNodes().size());
            assertEquals("dataCollector", chain.getNodes().get(0).getName());
            assertEquals("THEN", chain.getNodes().get(0).getType());
        }

        @Test
        @DisplayName("null 输入应抛出 RuleException")
        void shouldThrowOnNullYaml() {
            RuleException ex = assertThrows(RuleException.class, () -> parser.parseYaml(null));
            assertEquals("PARSE_001", ex.getErrorCode());
        }

        @Test
        @DisplayName("空字符串输入应抛出 RuleException")
        void shouldThrowOnEmptyYaml() {
            RuleException ex = assertThrows(RuleException.class, () -> parser.parseYaml(""));
            assertEquals("PARSE_001", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("parseJson 方法")
    class ParseJsonTests {

        @Test
        @DisplayName("应正确解析 JSON 规则链定义")
        void shouldParseJson() {
            String json = "{\"name\":\"testChain\",\"description\":\"测试链\"}";

            ChainDefinition chain = parser.parseJson(json);

            assertEquals("testChain", chain.getName());
            assertEquals("测试链", chain.getDescription());
        }

        @Test
        @DisplayName("null 输入应抛出 RuleException")
        void shouldThrowOnNullJson() {
            RuleException ex = assertThrows(RuleException.class, () -> parser.parseJson(null));
            assertEquals("PARSE_003", ex.getErrorCode());
        }

        @Test
        @DisplayName("空字符串输入应抛出 RuleException")
        void shouldThrowOnEmptyJson() {
            RuleException ex = assertThrows(RuleException.class, () -> parser.parseJson(""));
            assertEquals("PARSE_003", ex.getErrorCode());
        }

        @Test
        @DisplayName("非法 JSON 应抛出 RuleException")
        void shouldThrowOnInvalidJson() {
            assertThrows(RuleException.class, () -> parser.parseJson("not valid json"));
        }
    }

    @Nested
    @DisplayName("parseFile 方法")
    class ParseFileTests {

        @Test
        @DisplayName("null 文件路径应返回空列表")
        void shouldReturnEmptyForNullPath() {
            assertTrue(parser.parseFile(null).isEmpty());
        }

        @Test
        @DisplayName("空文件路径应返回空列表")
        void shouldReturnEmptyForEmptyPath() {
            assertTrue(parser.parseFile("").isEmpty());
        }

        @Test
        @DisplayName("不存在的文件应返回空列表")
        void shouldReturnEmptyForMissingFile() {
            assertTrue(parser.parseFile("/nonexistent/path/rule.yml").isEmpty());
        }
    }
}
