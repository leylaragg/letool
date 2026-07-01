package com.github.leyland.letool.net.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HexDumpUtil 十六进制转储工具测试")
class HexDumpUtilTest {

    @Nested
    @DisplayName("dumpHex - 完整数组转储")
    class DumpHexFull {

        @Test
        @DisplayName("空字节返回 {empty}")
        void dumpEmpty() {
            assertEquals("{empty}", HexDumpUtil.dumpHex(new byte[0]));
        }

        @Test
        @DisplayName("null 返回 {empty}")
        void dumpNull() {
            assertEquals("{empty}", HexDumpUtil.dumpHex(null));
        }

        @Test
        @DisplayName("精确 16 字节包含偏移量")
        void dumpExact16Bytes() {
            String result = HexDumpUtil.dumpHex(new byte[16]);
            assertNotEquals("{empty}", result);
            assertTrue(result.startsWith("0000"));
        }

        @Test
        @DisplayName("少于 16 字节仅输出一行")
        void dumpLessThan16Bytes() {
            String result = HexDumpUtil.dumpHex(new byte[10]);
            assertNotEquals("{empty}", result);
            assertTrue(result.startsWith("0000"));
        }

        @Test
        @DisplayName("多于 16 字节输出多行")
        void dumpMoreThan16Bytes() {
            String result = HexDumpUtil.dumpHex(new byte[20]);
            assertNotEquals("{empty}", result);
            assertTrue(result.contains("0010"));
        }
    }

    @Nested
    @DisplayName("dumpHex(range) - 指定范围转储")
    class DumpHexRange {

        @Test
        @DisplayName("截取前 5 字节")
        void dumpFirst5Bytes() {
            byte[] data = {0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00, 0x00};
            String result = HexDumpUtil.dumpHex(data, 0, 5);
            assertNotEquals("{empty}", result);
            assertTrue(result.contains("48"));
            assertTrue(result.contains("65"));
            assertTrue(result.contains("Hello"));
        }

        @Test
        @DisplayName("负数 offset 视为 0")
        void dumpNegativeOffset() {
            byte[] data = {0x01, 0x02};
            String result = HexDumpUtil.dumpHex(data, -1, 1);
            assertNotEquals("{empty}", result);
        }

        @Test
        @DisplayName("length 0 返回 {empty}")
        void dumpZeroLength() {
            assertEquals("{empty}", HexDumpUtil.dumpHex(new byte[]{1, 2, 3}, 0, 0));
        }

        @Test
        @DisplayName("null 或空数组返回 {empty}")
        void dumpNull() {
            assertEquals("{empty}", HexDumpUtil.dumpHex(null, 0, 5));
            assertEquals("{empty}", HexDumpUtil.dumpHex(new byte[0], 0, 5));
        }
    }
}
