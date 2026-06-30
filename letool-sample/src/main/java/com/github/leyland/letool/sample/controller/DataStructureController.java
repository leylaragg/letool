package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.datastructure.chain.DecisionChain;
import com.github.leyland.letool.datastructure.tree.TreeBuilder;
import com.github.leyland.letool.sample.entity.TreeNode;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 演示 letool-starter-data-structure 数据结构 —— 树构建 + 决策链.
 */
@RestController
@RequestMapping("/api/public/datastructure")
public class DataStructureController {

    /**
     * 树构建 —— 将平列表构建为树形结构.
     * <pre>{@code
     *  总公司
     *  ├── 技术部
     *  │    ├── 后端组
     *  │    └── 前端组
     *  └── 市场部
     * }</pre>
     */
    @GetMapping("/tree")
    public R<List<TreeNode>> tree() {
        List<TreeNode> flatList = Arrays.asList(
                new TreeNode(1L, 0L, "总公司"),
                new TreeNode(2L, 1L, "技术部"),
                new TreeNode(3L, 1L, "市场部"),
                new TreeNode(4L, 2L, "后端组"),
                new TreeNode(5L, 2L, "前端组")
        );
        List<TreeNode> tree = TreeBuilder.build(flatList);
        return R.ok(tree);
    }

    /**
     * 决策链 —— 消除 if-else 的条件匹配.
     * <p>
     * GET /api/public/datastructure/decision?amount=15000 -> "主管审批"
     * GET /api/public/datastructure/decision?amount=100 -> "自动通过"
     */
    @GetMapping("/decision")
    public R<Map<String, Object>> decision(@RequestParam(defaultValue = "1000") int amount) {
        DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                .when(a -> a > 50000, a -> "风控审核")
                .when(a -> a > 10000, a -> "主管审批")
                .when(a -> a > 1000, a -> "经理审批")
                .otherwise(a -> "自动通过")
                .build();

        String result = chain.execute(amount);
        return R.ok(Map.of("amount", amount, "decision", result));
    }
}
