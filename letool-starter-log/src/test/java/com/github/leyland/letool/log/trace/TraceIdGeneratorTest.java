package com.github.leyland.letool.log.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdGeneratorTest {

    @Test
    void uuidShort_shouldReturnNonEmptyString() {
        String id = TraceIdGenerator.uuidShort();
        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    void uuidShort_shouldRemoveDashes() {
        String id = TraceIdGenerator.uuidShort();
        assertFalse(id.contains("-"));
    }

    @Test
    void uuidShort_shouldGenerateUniqueIds() {
        String id1 = TraceIdGenerator.uuidShort();
        String id2 = TraceIdGenerator.uuidShort();
        assertNotEquals(id1, id2);
    }

    @Test
    void snowflakeShort_shouldReturnNonEmptyString() {
        String id = TraceIdGenerator.snowflakeShort();
        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    void snowflakeShort_shouldGenerateUniqueIds() {
        String id1 = TraceIdGenerator.snowflakeShort();
        String id2 = TraceIdGenerator.snowflakeShort();
        assertNotEquals(id1, id2);
    }
}
