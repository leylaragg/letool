package io.github.leylaragg.letool.datastructure.linked;

import io.github.leylaragg.letool.datastructure.exception.DataStructureErrorCode;
import io.github.leylaragg.letool.datastructure.exception.DataStructureException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单向和双向链表的关键生产契约测试。
 */
class LinkedNodeTest {

    /**
     * 验证单向链表连接方法返回被连接节点，并支持安全遍历与计数。
     */
    @Test
    void shouldBuildAndTraverseSinglyLinkedList() {
        LinkedNode<String> head = LinkedNode.of("a");
        LinkedNode<String> middle = head.next("b");
        LinkedNode<String> tail = middle.nextNode(LinkedNode.of("c"));
        List<String> values = new ArrayList<>();

        head.forEach(values::add);

        assertSame(tail, middle.getNext());
        assertEquals(List.of("a", "b", "c"), values);
        assertEquals(3, head.count());
        assertTrue(head.hasNext());
        assertFalse(tail.hasNext());
    }

    /**
     * 验证连接入口拒绝自环和包含当前节点的候选链。
     */
    @Test
    void shouldRejectCycleCreatingConnections() {
        LinkedNode<String> self = LinkedNode.of("self");
        assertError(DataStructureErrorCode.LINK_CYCLE_DETECTED, () -> self.setNext(self));

        LinkedNode<String> head = LinkedNode.of("a");
        LinkedNode<String> tail = head.next("b");
        assertError(DataStructureErrorCode.LINK_CYCLE_DETECTED, () -> tail.nextNode(head));
    }

    /**
     * 验证遍历器能够识别由子类或外部恢复过程带入的损坏环形链路。
     */
    @Test
    void shouldDetectCorruptedCycleDuringTraversal() {
        CorruptibleNode<String> first = new CorruptibleNode<>("a");
        CorruptibleNode<String> second = new CorruptibleNode<>("b");
        first.corruptNext(second);
        second.corruptNext(first);

        assertError(DataStructureErrorCode.LINK_CYCLE_DETECTED, first::count);
        assertError(DataStructureErrorCode.LINK_CYCLE_DETECTED, () -> first.forEach(ignored -> { }));

        Iterator<String> iterator = first.iterator();
        assertEquals("a", iterator.next());
        assertEquals("b", iterator.next());
        assertError(DataStructureErrorCode.LINK_CYCLE_DETECTED, iterator::next);
    }

    /**
     * 验证空回调被统一拒绝，节点相等性和哈希值不依赖可变数据。
     */
    @Test
    void shouldUseIdentitySemanticsAndValidateConsumer() {
        LinkedNode<String> first = LinkedNode.of("same");
        LinkedNode<String> second = LinkedNode.of("same");
        int originalHash = first.hashCode();

        assertNotEquals(first, second);
        first.setData("changed");
        assertEquals(originalHash, first.hashCode());
        assertError(DataStructureErrorCode.INVALID_ARGUMENT, () -> first.forEach(null));
    }

    /**
     * 验证继承的链式入口仍创建双向节点，并保持前后导航一致。
     */
    @Test
    void shouldBuildDoublyLinkedListThroughAllConvenienceEntrances() {
        DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
        DoublyLinkedNode<String> middle = head.next("b");
        DoublyLinkedNode<String> tail = middle.append("c");
        List<String> reverseValues = new ArrayList<>();

        tail.forEachReverse(reverseValues::add);

        assertSame(head, middle.getPrev());
        assertSame(middle, tail.getPrev());
        assertSame(tail, head.tail());
        assertSame(head, tail.head());
        assertEquals(List.of("c", "b", "a"), reverseValues);
    }

    /**
     * 验证替换和解除后继时同步维护旧、新节点的前驱引用。
     */
    @Test
    void shouldKeepBothDirectionsConsistentWhenReplacingLinks() {
        DoublyLinkedNode<String> head = DoublyLinkedNode.of("head");
        DoublyLinkedNode<String> oldNext = head.append("old");
        DoublyLinkedNode<String> replacement = DoublyLinkedNode.of("replacement");

        head.setNext(replacement);

        assertNull(oldNext.getPrev());
        assertSame(head, replacement.getPrev());
        assertSame(replacement, head.getNext());

        head.setNext(null);
        assertNull(head.getNext());
        assertNull(replacement.getPrev());
    }

    /**
     * 验证已有节点连接、前插和前驱设置不会静默破坏其他双向链路。
     */
    @Test
    void shouldRejectNodeOwnershipConflictsAndPlainNodes() {
        DoublyLinkedNode<String> firstHead = DoublyLinkedNode.of("first");
        DoublyLinkedNode<String> shared = DoublyLinkedNode.of("shared");
        assertSame(shared, firstHead.appendNode(shared));

        DoublyLinkedNode<String> secondHead = DoublyLinkedNode.of("second");
        assertError(DataStructureErrorCode.INVALID_LINK, () -> secondHead.appendNode(shared));
        assertError(DataStructureErrorCode.INVALID_LINK, () -> firstHead.setNext(LinkedNode.of("plain")));

        DoublyLinkedNode<String> standalone = DoublyLinkedNode.of("standalone");
        standalone.setPrev(secondHead);
        assertSame(standalone, secondHead.getNext());
        assertSame(secondHead, standalone.getPrev());

        DoublyLinkedNode<String> newHead = firstHead.prepend("before");
        assertSame(newHead, firstHead.getPrev());
        assertSame(firstHead, newHead.getNext());
    }

    /**
     * 断言操作抛出指定稳定错误码。
     *
     * @param errorCode 预期错误码
     * @param action 待执行操作
     */
    private static void assertError(DataStructureErrorCode errorCode, Runnable action) {
        DataStructureException exception = assertThrows(DataStructureException.class, action::run);
        assertEquals(errorCode, exception.getErrorCode());
    }

    /**
     * 用于模拟外部反序列化或不受信任子类写坏链路的节点。
     *
     * @param <T> 节点数据类型
     */
    private static final class CorruptibleNode<T> extends LinkedNode<T> {

        /** 绕过正常连接入口写入的后继。 */
        private LinkedNode<T> rawNext;

        /**
         * 创建可模拟损坏链路的节点。
         *
         * @param data 节点数据
         */
        private CorruptibleNode(T data) {
            super(data);
        }

        /**
         * 直接写入后继，仅用于构造损坏拓扑。
         *
         * @param next 后继节点
         */
        private void corruptNext(LinkedNode<T> next) {
            this.rawNext = next;
        }

        /** {@inheritDoc} */
        @Override
        public LinkedNode<T> getNext() {
            return rawNext;
        }

        /** {@inheritDoc} */
        @Override
        public void setNext(LinkedNode<T> next) {
            this.rawNext = next;
        }
    }
}
