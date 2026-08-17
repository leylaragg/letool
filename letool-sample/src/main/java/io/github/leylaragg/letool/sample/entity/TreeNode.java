package io.github.leylaragg.letool.sample.entity;

import java.util.List;

/**
 * 演示 Data Structure 泛型树节点契约的部门实体。
 *
 * <p>根节点使用空父节点 ID，构建成功后由树构建器回填非空子节点列表。</p>
 */
public class TreeNode implements io.github.leylaragg.letool.datastructure.tree.TreeNode<TreeNode> {

    /** 节点唯一标识。 */
    private Long id;

    /** 父节点标识，根节点为空。 */
    private Long parentId;

    /** 部门名称。 */
    private String name;

    /** 构建器回填的子部门列表。 */
    private List<TreeNode> children;

    /**
     * 创建空部门节点。
     */
    public TreeNode() {
    }

    /**
     * 创建部门节点。
     *
     * @param id 节点唯一标识
     * @param parentId 父节点标识；根节点传入空值
     * @param name 部门名称
     */
    public TreeNode(Long id, Long parentId, String name) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 修改节点唯一标识。
     *
     * @param id 新节点唯一标识
     */
    public void setId(Long id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    public Long getParentId() {
        return parentId;
    }

    /**
     * 修改父节点标识。
     *
     * @param parentId 新父节点标识；根节点传入空值
     */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /**
     * 获取部门名称。
     *
     * @return 部门名称
     */
    public String getName() {
        return name;
    }

    /**
     * 修改部门名称。
     *
     * @param name 新部门名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public List<TreeNode> getChildren() {
        return children;
    }

    /** {@inheritDoc} */
    @Override
    public void setChildren(List<TreeNode> children) {
        this.children = children;
    }
}
