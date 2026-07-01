package com.github.leyland.letool.rule.chain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChainDefinition 测试")
class ChainDefinitionTest {

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应初始化空的 nodes 列表")
        void shouldInitializeEmptyNodes() {
            ChainDefinition chain = new ChainDefinition();
            assertNotNull(chain.getNodes());
            assertTrue(chain.getNodes().isEmpty());
        }

        @Test
        @DisplayName("带 name 参数的构造应设置名称")
        void shouldSetName() {
            ChainDefinition chain = new ChainDefinition("riskChain");
            assertEquals("riskChain", chain.getName());
            assertNotNull(chain.getNodes());
        }
    }

    @Nested
    @DisplayName("fromJson 静态工厂方法")
    class FromJsonTests {

        @Test
        @DisplayName("fromJson 应正确解析 JSON 字符串")
        void shouldParseJson() {
            String json = "{\"name\":\"testChain\",\"description\":\"测试链\"}";

            ChainDefinition chain = ChainDefinition.fromJson(json);

            assertEquals("testChain", chain.getName());
            assertEquals("测试链", chain.getDescription());
        }
    }

    @Nested
    @DisplayName("getter/setter 操作")
    class GetterSetterTests {

        @Test
        @DisplayName("setName 和 getName 应正确存取")
        void shouldSetAndGetName() {
            ChainDefinition chain = new ChainDefinition();
            chain.setName("fraudDetection");
            assertEquals("fraudDetection", chain.getName());
        }

        @Test
        @DisplayName("setDescription 和 getDescription 应正确存取")
        void shouldSetAndGetDescription() {
            ChainDefinition chain = new ChainDefinition();
            chain.setDescription("反欺诈检测规则链");
            assertEquals("反欺诈检测规则链", chain.getDescription());
        }

        @Test
        @DisplayName("setNodes 应正确替换节点列表")
        void shouldReplaceNodes() {
            ChainDefinition chain = new ChainDefinition();
            chain.addNode(new ChainDefinition.NodeDefinition("nodeA"));

            assertEquals(1, chain.getNodes().size());
        }
    }

    @Nested
    @DisplayName("addNode 操作")
    class AddNodeTests {

        @Test
        @DisplayName("addNode 应添加节点到根节点列表")
        void shouldAddNode() {
            ChainDefinition chain = new ChainDefinition("testChain");
            chain.addNode(new ChainDefinition.NodeDefinition("step1"));
            chain.addNode(new ChainDefinition.NodeDefinition("step2"));

            assertEquals(2, chain.getNodes().size());
            assertEquals("step1", chain.getNodes().get(0).getName());
            assertEquals("step2", chain.getNodes().get(1).getName());
        }
    }

    @Nested
    @DisplayName("NodeDefinition 内部类")
    class NodeDefinitionTests {

        @Test
        @DisplayName("无参构造应初始化空列表")
        void shouldInitializeEmpty() {
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition();
            assertNotNull(node.getChildren());
            assertNotNull(node.getProperties());
        }

        @Test
        @DisplayName("带 name 的构造应设置名称")
        void shouldSetNodeName() {
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("validator");
            assertEquals("validator", node.getName());
        }

        @Test
        @DisplayName("带 name 和 type 的构造应设置名称和类型")
        void shouldSetNameAndType() {
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("gateway", "IF");
            assertEquals("gateway", node.getName());
            assertEquals("IF", node.getType());
        }

        @Test
        @DisplayName("addChild 应添加子节点")
        void shouldAddChild() {
            ChainDefinition.NodeDefinition parent = new ChainDefinition.NodeDefinition("parent", "THEN");
            parent.addChild(new ChainDefinition.NodeDefinition("child1"));
            parent.addChild(new ChainDefinition.NodeDefinition("child2"));

            assertEquals(2, parent.getChildren().size());
        }

        @Test
        @DisplayName("setProperty 和 getProperty 应正确存取")
        void shouldSetAndGetProperty() {
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("node");
            node.setProperty("timeout", 5000);
            node.setProperty("retryCount", 3);

            assertEquals(5000, node.getProperty("timeout"));
            assertEquals(3, node.getProperty("retryCount"));
            assertNull(node.getProperty("nonexistent"));
        }

        @Test
        @DisplayName("setCondition 和 getCondition 应正确存取")
        void shouldSetAndGetCondition() {
            ChainDefinition.NodeDefinition node = new ChainDefinition.NodeDefinition("node");
            node.setCondition("amount > 10000");

            assertEquals("amount > 10000", node.getCondition());
        }
    }
}
