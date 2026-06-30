package com.github.leyland.letool.datastructure.linked;

/**
 * 标记接口 —— 表示节点具有指向下一个节点的引用，用于树或其他结构中需要链式遍历的场景.
 *
 * <pre>{@code
 * public class MyBizNode implements INext<MyBizNode> {
 *     private MyBizNode next;
 *     public MyBizNode getNext() { return next; }
 *     public void setNext(MyBizNode next) { this.next = next; }
 * }
 * }</pre>
 *
 * @param <T> 实现类自身类型
 * @author leyland
 * @since 2.0.0
 */
public interface INext<T extends INext<T>> {

    /** 获取下一个节点. */
    T getNext();

    /** 设置下一个节点. */
    void setNext(T next);

    /** 是否还有下一个节点. */
    default boolean hasNext() {
        return getNext() != null;
    }
}
