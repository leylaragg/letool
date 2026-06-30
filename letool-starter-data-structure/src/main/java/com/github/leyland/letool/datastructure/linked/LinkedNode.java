package com.github.leyland.letool.datastructure.linked;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单向链表节点 —— 持有数据负载和指向下一个节点的引用，提供链式构建和迭代遍历.
 *
 * <pre>{@code
 * // 链式构建
 * LinkedNode<String> head = LinkedNode.of("a").next("b").next("c");
 *
 * // 迭代遍历
 * for (String s : head) { System.out.println(s); }
 *
 * // Consumer 遍历
 * head.forEach(System.out::println);
 * }</pre>
 *
 * @param <T> 节点存储的数据类型
 * @author leyland
 * @since 2.0.0
 */
public class LinkedNode<T> implements INext<LinkedNode<T>>, Iterable<T> {

    private T data;
    private LinkedNode<T> next;

    public LinkedNode() {}

    public LinkedNode(T data) {
        this.data = data;
    }

    public static <T> LinkedNode<T> of(T data) {
        return new LinkedNode<>(data);
    }

    /** 设置下一个节点并返回下一个节点（便于链式构建）. */
    public LinkedNode<T> next(T data) {
        LinkedNode<T> node = new LinkedNode<>(data);
        this.next = node;
        return node;
    }

    /** 直接设置下一个节点. */
    public LinkedNode<T> nextNode(LinkedNode<T> node) {
        this.next = node;
        return this;
    }

    /** 从当前节点开始遍历，对每个节点执行 action. */
    public void forEach(Consumer<? super T> action) {
        LinkedNode<T> current = this;
        while (current != null) {
            action.accept(current.data);
            current = current.next;
        }
    }

    /** 统计当前节点开始的链表长度. */
    public int count() {
        int count = 0;
        LinkedNode<T> current = this;
        while (current != null) {
            count++;
            current = current.next;
        }
        return count;
    }

    @Override
    public Iterator<T> iterator() {
        return new LinkedNodeIterator<>(this);
    }

    // ---- getters / setters ----

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    @Override
    public LinkedNode<T> getNext() { return next; }

    @Override
    public void setNext(LinkedNode<T> next) { this.next = next; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkedNode)) return false;
        LinkedNode<?> that = (LinkedNode<?>) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() { return Objects.hash(data); }

    @Override
    public String toString() { return "Node{" + data + "}"; }

    private static class LinkedNodeIterator<T> implements Iterator<T> {
        private LinkedNode<T> current;
        LinkedNodeIterator(LinkedNode<T> head) { this.current = head; }
        @Override public boolean hasNext() { return current != null; }
        @Override public T next() {
            if (current == null) throw new NoSuchElementException();
            T data = current.data;
            current = current.next;
            return data;
        }
    }
}
