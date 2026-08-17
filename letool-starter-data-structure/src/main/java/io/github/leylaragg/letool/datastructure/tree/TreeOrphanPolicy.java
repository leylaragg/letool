package io.github.leylaragg.letool.datastructure.tree;

/**
 * 树构建遇到孤儿节点时采用的处理策略。
 *
 * <p>孤儿节点是指 {@link TreeNode#getParentId()} 不为空，但对应父节点不在本次平列表中的节点。
 * 默认应使用 {@link #REJECT} 暴露数据问题；只有业务明确接受降级时才选择 {@link #AS_ROOT}。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum TreeOrphanPolicy {

    /**
     * 拒绝孤儿节点并抛出稳定异常。
     */
    REJECT,

    /**
     * 将孤儿节点提升为根节点，其后代仍按正常父子关系挂载。
     */
    AS_ROOT
}
