package io.github.leylaragg.letool.datastructure.linked;

import io.github.leylaragg.letool.datastructure.exception.DataStructureException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 支持防环连接和身份安全遍历的轻量单向链表节点。
 *
 * <p>节点采用对象身份相等语义，修改数据负载不会改变节点的哈希值。连接和数据修改不是线程安全操作；
 * 需要并发写入时，调用方应在业务层完成同步。</p>
 *
 * <pre>{@code
 * LinkedNode<String> head = LinkedNode.of("a");
 * LinkedNode<String> tail = head.next("b").next("c");
 * head.forEach(System.out::println);
 * }</pre>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class LinkedNode<T> implements INext<LinkedNode<T>>, Iterable<T> {

    /** 当前节点的数据负载。 */
    private T data;

    /** 当前节点的后继。 */
    private LinkedNode<T> next;

    /**
     * 创建数据为空的单向节点。
     */
    public LinkedNode() {
    }

    /**
     * 创建包含指定数据的单向节点。
     *
     * @param data 节点数据；允许为空
     */
    public LinkedNode(T data) {
        this.data = data;
    }

    /**
     * 创建包含指定数据的单向节点。
     *
     * @param data 节点数据；允许为空
     * @param <T> 节点数据类型
     * @return 新节点
     */
    public static <T> LinkedNode<T> of(T data) {
        return new LinkedNode<>(data);
    }

    /**
     * 创建后继节点、建立连接并返回新节点，便于继续链式追加。
     *
     * @param data 后继节点数据；允许为空
     * @return 新创建的后继节点
     */
    public LinkedNode<T> next(T data) {
        LinkedNode<T> node = new LinkedNode<>(data);
        setNext(node);
        return node;
    }

    /**
     * 连接已有节点并返回被连接节点，便于继续链式追加。
     *
     * @param node 待连接节点，不允许为空
     * @return 被连接节点
     * @throws DataStructureException 当节点为空或连接会产生环时抛出
     */
    public LinkedNode<T> nextNode(LinkedNode<T> node) {
        if (node == null) {
            throw DataStructureException.invalidArgument("nextNode");
        }
        setNext(node);
        return node;
    }

    /**
     * 从当前节点开始依次处理每个数据负载。
     *
     * @param action 数据处理器
     * @throws DataStructureException 当处理器为空或遍历时检测到环时抛出
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        if (action == null) {
            throw DataStructureException.invalidArgument("action");
        }
        for (T value : this) {
            action.accept(value);
        }
    }

    /**
     * 统计从当前节点开始的节点数量。
     *
     * @return 节点数量，至少为一
     * @throws DataStructureException 当遍历时检测到环时抛出
     */
    public int count() {
        int count = 0;
        for (T ignored : this) {
            count++;
        }
        return count;
    }

    /**
     * 创建从当前节点开始的数据迭代器。
     *
     * @return 具备环检测能力的迭代器
     */
    @Override
    public Iterator<T> iterator() {
        return new LinkedNodeIterator<>(this);
    }

    /**
     * 获取节点数据。
     *
     * @return 节点数据；可能为空
     */
    public T getData() {
        return data;
    }

    /**
     * 修改节点数据。
     *
     * @param data 新数据；允许为空
     */
    public void setData(T data) {
        this.data = data;
    }

    /** {@inheritDoc} */
    @Override
    public LinkedNode<T> getNext() {
        return next;
    }

    /**
     * 设置后继节点。连接前会验证候选链，防止产生环；传入空值用于解除连接。
     *
     * @param next 后继节点；为空时解除当前连接
     * @throws DataStructureException 当候选链已经有环或连接后会产生环时抛出
     */
    @Override
    public void setNext(LinkedNode<T> next) {
        if (next != null) {
            validateNextCandidate(next);
        }
        this.next = next;
    }

    /**
     * 验证候选后继链不会形成环。
     *
     * @param candidate 候选后继节点
     * @throws DataStructureException 当候选链存在环或包含当前节点时抛出
     */
    protected final void validateNextCandidate(LinkedNode<T> candidate) {
        Set<LinkedNode<T>> visited = identitySet();
        LinkedNode<T> current = candidate;
        while (current != null) {
            if (current == this || !visited.add(current)) {
                throw DataStructureException.linkCycleDetected();
            }
            current = current.getNext();
        }
    }

    /**
     * 返回适合诊断的节点文本。
     *
     * @return 节点文本
     */
    @Override
    public String toString() {
        return "Node{" + data + "}";
    }

    /**
     * 创建按节点对象身份比较的集合。
     *
     * @param <T> 节点数据类型
     * @return 身份集合
     */
    private static <T> Set<LinkedNode<T>> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * 具备环检测能力的单向链表数据迭代器。
     *
     * @param <T> 节点数据类型
     */
    private static final class LinkedNodeIterator<T> implements Iterator<T> {

        /** 尚未返回数据的当前节点。 */
        private LinkedNode<T> current;

        /** 已经返回数据的节点身份集合。 */
        private final Set<LinkedNode<T>> visited = identitySet();

        /**
         * 创建迭代器。
         *
         * @param head 遍历起点
         */
        private LinkedNodeIterator(LinkedNode<T> head) {
            this.current = head;
        }

        /** {@inheritDoc} */
        @Override
        public boolean hasNext() {
            return current != null;
        }

        /**
         * 返回当前节点数据并移动到后继。
         *
         * @return 当前节点数据
         * @throws NoSuchElementException 当已经到达链尾时抛出
         * @throws DataStructureException 当检测到环时抛出
         */
        @Override
        public T next() {
            if (current == null) {
                throw new NoSuchElementException("No more linked node elements");
            }
            if (!visited.add(current)) {
                throw DataStructureException.linkCycleDetected();
            }
            T value = current.getData();
            current = current.getNext();
            return value;
        }
    }
}
