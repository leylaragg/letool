package io.github.leylaragg.letool.sample.controller;

import io.github.leylaragg.letool.sample.entity.TreeNode;
import io.github.leylaragg.letool.tool.model.R;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据结构示例控制器的关键契约测试。
 */
class DataStructureControllerTest {

    /**
     * 验证树示例使用生产化根节点契约并返回完整部门树。
     */
    @Test
    void shouldBuildCompleteDepartmentTree() {
        DataStructureController controller = new DataStructureController();

        R<List<TreeNode>> response = controller.tree();

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().size());
        TreeNode root = response.getData().get(0);
        assertEquals("总公司", root.getName());
        assertEquals(2, root.getChildren().size());
        assertEquals(5, countNodes(response.getData()));
    }

    /**
     * 非递归统计示例树节点数量。
     *
     * @param roots 根节点列表
     * @return 节点总数
     */
    private static int countNodes(List<TreeNode> roots) {
        int count = 0;
        List<TreeNode> currentLevel = roots;
        while (!currentLevel.isEmpty()) {
            count += currentLevel.size();
            currentLevel = currentLevel.stream()
                    .flatMap(node -> node.getChildren().stream())
                    .toList();
        }
        return count;
    }
}
