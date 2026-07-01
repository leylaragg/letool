package com.github.leyland.letool.net.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ByteBufUtil 字节缓冲区工具测试")
class ByteBufUtilTest {

    @Nested
    @DisplayName("concat - 字节数组拼接")
    class Concat {

        @Test
        @DisplayName("正常拼接多个数组")
        void concatMultiple() {
            byte[] a = {1, 2};
            byte[] b = {3, 4};
            byte[] c = {5};
            byte[] result = ByteBufUtil.concat(a, b, c);
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, result);
        }

        @Test
        @DisplayName("包含 null 的拼接跳过 null")
        void concatWithNull() {
            byte[] a = {1, 2};
            byte[] result = ByteBufUtil.concat(a, null, null);
            assertArrayEquals(new byte[]{1, 2}, result);
        }

        @Test
        @DisplayName("全 null 或空参数返回空数组")
        void concatAllNull() {
            assertEquals(0, ByteBufUtil.concat(null, null).length);
            assertEquals(0, ByteBufUtil.concat().length);
        }

        @Test
        @DisplayName("空数组参数返回空数组")
        void concatEmpty() {
            assertEquals(0, ByteBufUtil.concat(new byte[0], new byte[0]).length);
        }
    }

    @Nested
    @DisplayName("subArray - 子数组截取")
    class SubArray {

        @Test
        @DisplayName("从开头截取")
        void subArrayFromStart() {
            byte[] src = {1, 2, 3, 4, 5};
            byte[] result = ByteBufUtil.subArray(src, 0, 2);
            assertArrayEquals(new byte[]{1, 2}, result);
        }

        @Test
        @DisplayName("从中间截取")
        void subArrayFromMiddle() {
            byte[] src = {1, 2, 3, 4, 5};
            byte[] result = ByteBufUtil.subArray(src, 2, 3);
            assertArrayEquals(new byte[]{3, 4, 5}, result);
        }

        @Test
        @DisplayName("src 为 null 返回 null")
        void subArrayNullSrc() {
            assertNull(ByteBufUtil.subArray(null, 0, 1));
        }

        @Test
        @DisplayName("越界抛出 ArrayIndexOutOfBoundsException")
        void subArrayOutOfBounds() {
            byte[] src = {1, 2, 3};
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> ByteBufUtil.subArray(src, 1, 3));
        }
    }

    @Nested
    @DisplayName("indexOf - 子数组查找")
    class IndexOf {

        @Test
        @DisplayName("找到目标返回正确偏移")
        void indexOfFound() {
            byte[] src = {0, 1, 2, 3, 4, 5};
            byte[] target = {2, 3};
            assertEquals(2, ByteBufUtil.indexOf(src, target));
        }

        @Test
        @DisplayName("未找到返回 -1")
        void indexOfNotFound() {
            byte[] src = {0, 1, 2, 3};
            byte[] target = {9, 9};
            assertEquals(-1, ByteBufUtil.indexOf(src, target));
        }

        @Test
        @DisplayName("任何参数为 null 返回 -1")
        void indexOfNull() {
            assertEquals(-1, ByteBufUtil.indexOf(null, new byte[]{1}));
            assertEquals(-1, ByteBufUtil.indexOf(new byte[]{1}, null));
        }

        @Test
        @DisplayName("空数组返回 -1")
        void indexOfEmpty() {
            assertEquals(-1, ByteBufUtil.indexOf(new byte[0], new byte[]{1}));
            assertEquals(-1, ByteBufUtil.indexOf(new byte[]{1}, new byte[0]));
        }
    }

    @Nested
    @DisplayName("toHex - 十六进制转换")
    class ToHex {

        @Test
        @DisplayName("标准十六进制转换（大写）")
        void toHexStandard() {
            assertEquals("0F1AFF", ByteBufUtil.toHex(new byte[]{15, 26, -1}));
        }

        @Test
        @DisplayName("空数组返回空串")
        void toHexEmpty() {
            assertEquals("", ByteBufUtil.toHex(new byte[0]));
        }

        @Test
        @DisplayName("null 返回空串")
        void toHexNull() {
            assertEquals("", ByteBufUtil.toHex(null));
        }

        @Test
        @DisplayName("单字节零值")
        void toHexZero() {
            assertEquals("00", ByteBufUtil.toHex(new byte[]{0}));
        }
    }

    @Nested
    @DisplayName("isEmpty / isNotEmpty")
    class Empty {

        @Test
        @DisplayName("null 或空数组判断为空")
        void empty() {
            assertTrue(ByteBufUtil.isEmpty(null));
            assertTrue(ByteBufUtil.isEmpty(new byte[0]));
        }

        @Test
        @DisplayName("非空数组判断为非空")
        void notEmpty() {
            assertFalse(ByteBufUtil.isEmpty(new byte[]{1}));
            assertTrue(ByteBufUtil.isNotEmpty(new byte[]{1}));
        }
    }
}
