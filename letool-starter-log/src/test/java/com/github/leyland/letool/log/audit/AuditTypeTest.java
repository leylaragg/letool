package com.github.leyland.letool.log.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditType 审计类型枚举测试")
class AuditTypeTest {

    @Test
    @DisplayName("四个枚举值")
    void testEnumValues() {
        assertEquals(4, AuditType.values().length);
        assertNotNull(AuditType.valueOf("AUTH"));
        assertNotNull(AuditType.valueOf("ADMIN"));
        assertNotNull(AuditType.valueOf("BUSINESS"));
        assertNotNull(AuditType.valueOf("SYSTEM"));
    }

    @Test
    @DisplayName("valueOf 按名称获取")
    void testValueOf() {
        assertEquals(AuditType.AUTH, AuditType.valueOf("AUTH"));
        assertEquals(AuditType.ADMIN, AuditType.valueOf("ADMIN"));
        assertEquals(AuditType.BUSINESS, AuditType.valueOf("BUSINESS"));
        assertEquals(AuditType.SYSTEM, AuditType.valueOf("SYSTEM"));
    }
}
