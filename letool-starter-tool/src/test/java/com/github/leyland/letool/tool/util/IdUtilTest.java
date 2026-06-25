package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdUtilTest {

    @Test
    void nextId() {
        long id1 = IdUtil.nextId();
        long id2 = IdUtil.nextId();
        assertTrue(id1 > 0);
        assertTrue(id2 > id1);
    }

    @Test
    void nextIdStr() {
        String id = IdUtil.nextIdStr();
        assertNotNull(id);
        assertTrue(Long.parseLong(id) > 0);
    }

    @Test
    void simpleUUID() {
        String uuid = IdUtil.simpleUUID();
        assertEquals(32, uuid.length());
        assertFalse(uuid.contains("-"));
    }

    @Test
    void uuid() {
        String uuid = IdUtil.uuid();
        assertEquals(36, uuid.length());
        assertTrue(uuid.contains("-"));
    }

    @Test
    void nanoId() {
        String id = IdUtil.nanoId();
        assertEquals(21, id.length());
        String id10 = IdUtil.nanoId(10);
        assertEquals(10, id10.length());
    }

    @Test
    void snowflakeUniqueness() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            ids.add(IdUtil.nextId());
        }
        assertEquals(10000, ids.size());
    }
}
