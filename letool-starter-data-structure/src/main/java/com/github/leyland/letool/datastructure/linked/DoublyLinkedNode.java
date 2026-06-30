package com.github.leyland.letool.datastructure.linked;

import java.util.Objects;

/**
 * 双向链表节点 —— 同时持有前驱和后继引用的链表节点，支持双向遍历.
 *
 * <pre>{@code
 * DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
 * DoublyLinkedNode<String> mid = head.append("b");
 * DoublyLinkedNode<String> tail = mid.append("c");
 *
 * // 正向遍历
 * head.forEach(System.out::println);  // a → b → c
 *
 * // 反向遍历
 * tail.forEachReverse(System.out::println);  // c → b → a
 * }</pre>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class DoublyLinkedNode<T> extends LinkedNode<T> {

    private DoublyLinkedNode<T> prev;

    public DoublyLinkedNode() {}

    public DoublyLinkedNode(T data) {
        super(data);
    }

    public static <T> DoublyLinkedNode<T> of(T data) {
        return new DoublyLinkedNode<>(data);
    }

    /** 在当前节点后追加新节点，自动建立双向链接. */
    public DoublyLinkedNode<T> append(T data) {
        DoublyLinkedNode<T> node = new DoublyLinkedNode<>(data);
        node.prev = this;
        setNext(node);
        return node;
    }

    /** 在当前节点后追加已有节点，自动建立双向链接. */
    public DoublyLinkedNode<T> appendNode(DoublyLinkedNode<T> node) {
        node.prev = this;
        setNext(node);
        return this;
    }

    /** 在当前节点前插入新节点，自动建立双向链接. */
    public DoublyLinkedNode<T> prepend(T data) {
        DoublyLinkedNode<T> node = new DoublyLinkedNode<>(data);
        node.setNext(this);
        node.prev = this.prev;
        if (this.prev != null) {
            this.prev.setNext(node);
        }
        this.prev = node;
        return node;
    }

    /** 反向遍历：从当前节点开始，沿 prev 方向依次执行 action. */
    public void forEachReverse(java.util.function.Consumer<? super T> action) {
        DoublyLinkedNode<T> current = this;
        while (current != null) {
            action.accept(current.getData());
            current = current.prev;
        }
    }

    /** 向前回溯到链表头部. */
    public DoublyLinkedNode<T> head() {
        DoublyLinkedNode<T> current = this;
        while (current.prev != null) {
            current = current.prev;
        }
        return current;
    }

    /** 向后移动到链表尾部. */
    public DoublyLinkedNode<T> tail() {
        DoublyLinkedNode<T> current = this;
        while (current.hasNext()) {
            current = (DoublyLinkedNode<T>) current.getNext();
        }
        return current;
    }

    // ---- getters / setters ----

    public DoublyLinkedNode<T> getPrev() { return prev; }
    public void setPrev(DoublyLinkedNode<T> prev) { this.prev = prev; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoublyLinkedNode)) return false;
        if (!super.equals(o)) return false;
        DoublyLinkedNode<?> that = (DoublyLinkedNode<?>) o;
        return Objects.equals(prev, that.prev);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), prev); }
}
