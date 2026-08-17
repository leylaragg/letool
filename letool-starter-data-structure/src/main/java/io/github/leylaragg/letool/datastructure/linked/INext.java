package io.github.leylaragg.letool.datastructure.linked;

/**
 * 定义具有后继引用的链式节点最小契约。
 *
 * <p>接口只描述访问能力，不规定连接校验规则。使用 {@link LinkedNode} 和
 * {@link DoublyLinkedNode} 时，应通过其连接方法获得防环和拓扑一致性保护。</p>
 *
 * @param <T> 实现类自身类型
 * @author leyland
 * @since 2.0.0
 */
public interface INext<T extends INext<T>> {

    /**
     * 获取当前节点的后继。
     *
     * @return 后继节点；没有后继时为 {@code null}
     */
    T getNext();

    /**
     * 设置当前节点的后继。
     *
     * @param next 后继节点；传入 {@code null} 表示解除连接
     */
    void setNext(T next);

    /**
     * 判断当前节点是否存在后继。
     *
     * @return 存在后继时为 {@code true}
     */
    default boolean hasNext() {
        return getNext() != null;
    }
}
