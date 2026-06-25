package com.github.leyland.letool.tool.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 泛型树节点模型——配合 {@link com.github.leyland.letool.tool.util.TreeUtil} 使用.
 *
 * <h3>设计特点</h3>
 * <ul>
 *   <li>泛型 {@code T} 承载业务数据，不强制业务对象实现任何接口</li>
 *   <li>{@code id} 和 {@code parentId} 使用 String 类型，兼容 UUID、雪花 ID、自增 ID 等</li>
 *   <li>{@code children} 默认为空列表（非 null），避免 NPE</li>
 *   <li>提供 {@link #isRoot()} 和 {@link #isLeaf()} 便捷判断</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 由 TreeUtil 自动构建
 * List<TreeNode<Dept>> tree = TreeUtil.buildTree(depts, Dept::getId, Dept::getParentId, Dept::getName);
 *
 * // 手动构建
 * TreeNode<User> root = TreeNode.of("1", "0", "总公司", null);
 * root.addChild(TreeNode.of("2", "1", "研发部", dept));
 * boolean isRoot = root.isRoot();  // true
 * boolean isLeaf = root.isLeaf();  // false
 * }</pre>
 *
 * @param <T> 业务数据类型（部门实体、菜单实体等）
 * @see com.github.leyland.letool.tool.util.TreeUtil
 */
public class TreeNode<T> {

    /** 节点唯一标识 */
    private String id;
    /** 父节点标识（"0" 或 null 或空字符串表示根节点） */
    private String parentId;
    /** 节点显示名称 */
    private String name;
    /** 挂载的业务数据 */
    private T data;
    /** 子节点列表（非 null） */
    private List<TreeNode<T>> children;

    /** 创建空节点（children 初始化为空列表）. */
    public TreeNode() {
        this.children = new ArrayList<>();
    }

    /**
     * 创建完整节点.
     *
     * @param id       节点唯一标识
     * @param parentId 父节点标识
     * @param name     显示名称
     * @param data     业务数据
     */
    public TreeNode(String id, String parentId, String name, T data) {
        this();
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.data = data;
    }

    /**
     * 静态工厂方法——创建节点.
     *
     * @param id       节点唯一标识
     * @param parentId 父节点标识
     * @param name     显示名称
     * @param data     业务数据
     * @param <T>      业务数据类型
     * @return 树节点
     */
    public static <T> TreeNode<T> of(String id, String parentId, String name, T data) {
        return new TreeNode<>(id, parentId, name, data);
    }

    /**
     * 添加子节点.
     *
     * @param child 子节点
     */
    public void addChild(TreeNode<T> child) {
        this.children.add(child);
    }

    /**
     * 判断是否为根节点.
     *
     * @return {@code true} 如果 parentId 为 null、空字符串 或 "0"
     */
    public boolean isRoot() {
        return parentId == null || parentId.isEmpty() || "0".equals(parentId);
    }

    /**
     * 判断是否为叶子节点（无子节点）.
     *
     * @return {@code true} 如果 children 为空列表
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    // ======================== getter / setter ========================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public List<TreeNode<T>> getChildren() { return children; }
    public void setChildren(List<TreeNode<T>> children) { this.children = children; }
}
