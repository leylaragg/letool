package com.github.leyland.letool.datastructure.linked;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("链表测试")
class LinkedNodeTest {

    @Nested
    @DisplayName("INext 接口默认方法")
    class INextDefaults {

        static class TestNode implements INext<TestNode> {
            private TestNode next;

            @Override public TestNode getNext() { return next; }
            @Override public void setNext(TestNode next) { this.next = next; }
        }

        @Test
        @DisplayName("有 next 时 hasNext 返回 true")
        void hasNextTrue() {
            TestNode a = new TestNode();
            a.setNext(new TestNode());
            assertTrue(a.hasNext());
        }

        @Test
        @DisplayName("无 next 时 hasNext 返回 false")
        void hasNextFalse() {
            assertFalse(new TestNode().hasNext());
        }
    }

    @Nested
    @DisplayName("LinkedNode 单向链表")
    class SinglyLinked {

        @Test
        @DisplayName("of 静态工厂创建")
        void of() {
            LinkedNode<String> node = LinkedNode.of("hello");
            assertEquals("hello", node.getData());
            assertNull(node.getNext());
        }

        @Test
        @DisplayName("next 链式构建")
        void nextChaining() {
            LinkedNode<String> head = LinkedNode.of("a");
            head.next("b").next("c");

            assertEquals("a", head.getData());
            assertEquals("b", head.getNext().getData());
            assertEquals("c", head.getNext().getNext().getData());
            assertNull(head.getNext().getNext().getNext());
        }

        @Test
        @DisplayName("nextNode 连接已有节点")
        void nextNode() {
            LinkedNode<String> a = LinkedNode.of("a");
            LinkedNode<String> b = LinkedNode.of("b");
            a.nextNode(b);

            assertSame(b, a.getNext());
        }

        @Test
        @DisplayName("forEach 遍历")
        void forEach() {
            LinkedNode<String> head = LinkedNode.of("a");
            head.next("b").next("c");

            List<String> collected = new ArrayList<>();
            head.forEach(collected::add);
            assertEquals(List.of("a", "b", "c"), collected);
        }

        @Test
        @DisplayName("count 统计长度")
        void count() {
            LinkedNode<String> head = LinkedNode.of("a");
            head.next("b").next("c");
            assertEquals(3, head.count());
        }

        @Test
        @DisplayName("单节点 count 为 1")
        void countSingle() {
            assertEquals(1, LinkedNode.of("a").count());
        }

        @Test
        @DisplayName("Iterable 增强 for 循环")
        void iterable() {
            LinkedNode<String> head = LinkedNode.of("x");
            head.next("y").next("z");

            List<String> result = new ArrayList<>();
            for (String s : head) {
                result.add(s);
            }
            assertEquals(List.of("x", "y", "z"), result);
        }
    }

    @Nested
    @DisplayName("DoublyLinkedNode 双向链表")
    class DoublyLinked {

        @Test
        @DisplayName("of 静态工厂")
        void of() {
            DoublyLinkedNode<Integer> node = DoublyLinkedNode.of(1);
            assertEquals(1, node.getData());
            assertNull(node.getPrev());
            assertNull(node.getNext());
        }

        @Test
        @DisplayName("append 追加并自动建立双向链接")
        void append() {
            DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
            DoublyLinkedNode<String> mid = head.append("b");
            DoublyLinkedNode<String> tail = mid.append("c");

            assertEquals("a", head.getData());
            assertEquals("b", mid.getData());
            assertEquals("c", tail.getData());
            assertNull(head.getPrev());
            assertSame(head, mid.getPrev());
            assertSame(mid, tail.getPrev());
        }

        @Test
        @DisplayName("prepend 前插并自动建立双向链接")
        void prepend() {
            DoublyLinkedNode<String> tail = DoublyLinkedNode.of("c");
            DoublyLinkedNode<String> mid = tail.prepend("b");
            DoublyLinkedNode<String> head = mid.prepend("a");

            assertEquals("a", head.getData());
            assertEquals("b", mid.getData());
            assertEquals("c", tail.getData());
            assertSame(head, mid.getPrev());
            assertSame(mid, tail.getPrev());
        }

        @Test
        @DisplayName("forEachReverse 反向遍历")
        void forEachReverse() {
            DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
            DoublyLinkedNode<String> tail = head.append("b").append("c");

            List<String> collected = new ArrayList<>();
            tail.forEachReverse(collected::add);
            assertEquals(List.of("c", "b", "a"), collected);
        }

        @Test
        @DisplayName("head 回溯到头部")
        void head() {
            DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
            DoublyLinkedNode<String> tail = head.append("b").append("c");

            assertSame(head, tail.head());
        }

        @Test
        @DisplayName("tail 移动到尾部")
        void tail() {
            DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
            DoublyLinkedNode<String> tail = head.append("b").append("c");

            assertSame(tail, head.tail());
        }
    }
}
