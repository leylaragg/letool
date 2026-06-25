package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnumUtilTest {

    enum Status {
        ACTIVE(1, "激活"),
        INACTIVE(0, "禁用");

        private final int code;
        private final String description;

        Status(int code, String desc) { this.code = code; this.description = desc; }
        public int getCode() { return code; }
        public String getDescription() { return description; }
    }

    @Test
    void getByName() {
        assertEquals(Status.ACTIVE, EnumUtil.getByName(Status.class, "ACTIVE"));
        assertNull(EnumUtil.getByName(Status.class, "UNKNOWN"));
    }

    @Test
    void getByCode() {
        assertEquals(Status.ACTIVE, EnumUtil.getByCode(Status.class, 1));
        assertEquals(Status.INACTIVE, EnumUtil.getByCode(Status.class, 0));
        assertNull(EnumUtil.getByCode(Status.class, 99));
    }

    @Test
    void toMap() {
        Map<String, Object> map = EnumUtil.toMap(Status.class);
        assertTrue(map.containsValue(1));
        assertTrue(map.containsValue(0));
    }
}
