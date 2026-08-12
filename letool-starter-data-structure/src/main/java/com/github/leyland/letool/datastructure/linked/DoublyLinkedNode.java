package com.github.leyland.letool.datastructure.linked;

import com.github.leyland.letool.datastructure.exception.DataStructureException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 自动维护前驱和后继一致性的轻量双向链表节点。
 *
 * <p>所有公开连接入口最终都经过 {@link #setNext(LinkedNode)} 校验。节点已有其他前驱时不会被
 * 静默抢占；调用方应先在原前驱上执行 {@code setNext(null)}，再建立新连接。</p>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class DoublyLinkedNode<T> extends LinkedNode<T> {

    /** 当前节点的前驱。 */
    private DoublyLinkedNode<T> prev;

    /**
     * 创建数据为空的双向节点。
     */
    public DoublyLinkedNode() {
    }

    /**
     * 创建包含指定数据的双向节点。
     *
     * @param data 节点数据；允许为空
     */
    public DoublyLinkedNode(T data) {
        super(data);
    }

    /**
     * 创建包含指定数据的双向节点。
     *
     * @param data 节点数据；允许为空
     * @param <T> 节点数据类型
     * @return 新双向节点
     */
    public static <T> DoublyLinkedNode<T> of(T data) {
        return new DoublyLinkedNode<>(data);
    }

    /**
     * 创建双向后继、建立连接并返回新节点。
     *
     * @param data 后继节点数据；允许为空
     * @return 新创建的双向后继
     */
    @Override
    public DoublyLinkedNode<T> next(T data) {
        DoublyLinkedNode<T> node = new DoublyLinkedNode<>(data);
        setNext(node);
        return node;
    }

    /**
     * 连接已有双向节点并返回被连接节点。
     *
     * @param node 待连接节点，不允许为空且必须是双向节点
     * @return 被连接的双向节点
     * @throws DataStructureException 当节点类型、归属或拓扑不符合约束时抛出
     */
    @Override
    public DoublyLinkedNode<T> nextNode(LinkedNode<T> node) {
        if (node == null) {
            throw DataStructureException.invalidArgument("nextNode");
        }
        setNext(node);
        return (DoublyLinkedNode<T>) node;
    }

    /**
     * 在当前节点后创建并追加双向节点。
     *
     * @param data 新节点数据；允许为空
     * @return 新追加节点
     */
    public DoublyLinkedNode<T> append(T data) {
        return next(data);
    }

    /**
     * 在当前节点后追加已有双向节点。
     *
     * @param node 待追加节点
     * @return 被追加节点
     * @throws DataStructureException 当节点为空、已有其他前驱或连接会产生环时抛出
     */
    public DoublyLinkedNode<T> appendNode(DoublyLinkedNode<T> node) {
        return nextNode(node);
    }

    /**
     * 在当前节点前创建并插入双向节点。
     *
     * @param data 新节点数据；允许为空
     * @return 新插入节点
     * @throws DataStructureException 当当前前驱关系已经损坏时抛出
     */
    public DoublyLinkedNode<T> prepend(T data) {
        DoublyLinkedNode<T> node = new DoublyLinkedNode<>(data);
        DoublyLinkedNode<T> oldPrev = prev;
        if (oldPrev == null) {
            node.setNext(this);
            return node;
        }
        if (oldPrev.getNext() != this) {
            throw DataStructureException.invalidLink("previousLink");
        }

        // 先让原前驱指向新节点，再由新节点接回当前节点，两个步骤都会维护对称引用。
        oldPrev.setNext(node);
        node.setNext(this);
        return node;
    }

    /**
     * 从当前节点开始沿前驱方向依次处理数据负载。
     *
     * @param action 数据处理器
     * @throws DataStructureException 当处理器为空或反向链路存在环时抛出
     */
    public void forEachReverse(Consumer<? super T> action) {
        if (action == null) {
            throw DataStructureException.invalidArgument("action");
        }
        Set<DoublyLinkedNode<T>> visited = identitySet();
        DoublyLinkedNode<T> current = this;
        while (current != null) {
            markVisited(visited, current);
            action.accept(current.getData());
            current = current.prev;
        }
    }

    /**
     * 沿前驱方向查找链表头部。
     *
     * @return 头节点
     * @throws DataStructureException 当反向链路存在环时抛出
     */
    public DoublyLinkedNode<T> head() {
        Set<DoublyLinkedNode<T>> visited = identitySet();
        DoublyLinkedNode<T> current = this;
        while (current.prev != null) {
            markVisited(visited, current);
            current = current.prev;
        }
        markVisited(visited, current);
        return current;
    }

    /**
     * 沿后继方向查找链表尾部。
     *
     * @return 尾节点
     * @throws DataStructureException 当正向链路存在环时抛出
     */
    public DoublyLinkedNode<T> tail() {
        Set<DoublyLinkedNode<T>> visited = identitySet();
        DoublyLinkedNode<T> current = this;
        while (current.getNext() != null) {
            markVisited(visited, current);
            current = current.getNext();
        }
        markVisited(visited, current);
        return current;
    }

    /**
     * 获取当前节点的双向后继。
     *
     * @return 双向后继；没有后继时为空
     */
    @Override
    public DoublyLinkedNode<T> getNext() {
        return (DoublyLinkedNode<T>) super.getNext();
    }

    /**
     * 设置双向后继并同步维护旧、新后继的前驱引用。
     *
     * @param next 新后继；为空时解除连接，非空时必须为双向节点
     * @throws DataStructureException 当节点类型、节点归属或拓扑不符合约束时抛出
     */
    @Override
    public void setNext(LinkedNode<T> next) {
        if (next != null && !(next instanceof DoublyLinkedNode<?>)) {
            throw DataStructureException.invalidLink("nextNodeType");
        }

        DoublyLinkedNode<T> typedNext = (DoublyLinkedNode<T>) next;
        DoublyLinkedNode<T> oldNext = getNext();
        if (typedNext == oldNext) {
            if (typedNext != null && typedNext.prev != this) {
                throw DataStructureException.invalidLink("nextPreviousLink");
            }
            return;
        }
        if (typedNext != null && typedNext.prev != null && typedNext.prev != this) {
            throw DataStructureException.invalidLink("nextOwnership");
        }

        // 父类入口先完成候选链防环校验，校验成功后再修改任何前驱引用。
        super.setNext(next);
        if (oldNext != null && oldNext.prev == this) {
            oldNext.prev = null;
        }
        if (typedNext != null) {
            typedNext.prev = this;
        }
    }

    /**
     * 获取当前节点的前驱。
     *
     * @return 前驱节点；没有前驱时为空
     */
    public DoublyLinkedNode<T> getPrev() {
        return prev;
    }

    /**
     * 设置前驱并由前驱的后继入口建立对称连接。
     *
     * @param prev 新前驱；为空时解除与原前驱的连接
     * @throws DataStructureException 当前节点已经属于其他前驱或连接会产生环时抛出
     */
    public void setPrev(DoublyLinkedNode<T> prev) {
        if (prev == this.prev) {
            return;
        }
        if (prev == null) {
            DoublyLinkedNode<T> oldPrev = this.prev;
            if (oldPrev == null) {
                return;
            }
            if (oldPrev.getNext() != this) {
                throw DataStructureException.invalidLink("previousLink");
            }
            oldPrev.setNext(null);
            return;
        }
        if (this.prev != null) {
            throw DataStructureException.invalidLink("previousOwnership");
        }
        prev.setNext(this);
    }

    /**
     * 标记已访问节点，重复出现时抛出环异常。
     *
     * @param visited 已访问节点集合
     * @param node 当前节点
     * @param <T> 节点数据类型
     */
    private static <T> void markVisited(
            Set<DoublyLinkedNode<T>> visited,
            DoublyLinkedNode<T> node) {
        if (!visited.add(node)) {
            throw DataStructureException.linkCycleDetected();
        }
    }

    /**
     * 创建按节点对象身份比较的集合。
     *
     * @param <T> 节点数据类型
     * @return 身份集合
     */
    private static <T> Set<DoublyLinkedNode<T>> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
