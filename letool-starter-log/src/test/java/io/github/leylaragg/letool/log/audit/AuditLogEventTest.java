package io.github.leylaragg.letool.log.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuditLogEvent} 审计事件模型测试。
 */
@DisplayName("AuditLogEvent 审计日志事件测试")
class AuditLogEventTest {

    /**
     * 新建事件应自动填充当前创建时间。
     */
    @Test
    @DisplayName("默认 createTime 为当前时间")
    void testDefaultCreateTime() {
        AuditLogEvent event = new AuditLogEvent();
        assertNotNull(event.getCreateTime());
        assertTrue(event.getCreateTime().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    /**
     * 所有可变字段应支持标准 JavaBean 访问。
     */
    @Test
    @DisplayName("setter/getter - 所有字段")
    void testAllSetters() {
        AuditLogEvent event = new AuditLogEvent();
        event.setTraceId("trace-123");
        event.setOperator("admin");
        event.setOperation("删除用户");
        event.setType(AuditType.ADMIN);
        event.setBizNo("1001");
        event.setResult("SUCCESS");
        event.setIp("192.168.1.100");
        event.setUserAgent("Mozilla/5.0");
        event.setDurationMs(150);
        event.setRequestBody("{\"userId\": 1}");
        event.setErrorMessage(null);
        event.setCreateTime(LocalDateTime.of(2025, 1, 15, 10, 30));

        assertEquals("trace-123", event.getTraceId());
        assertEquals("admin", event.getOperator());
        assertEquals("删除用户", event.getOperation());
        assertEquals(AuditType.ADMIN, event.getType());
        assertEquals("1001", event.getBizNo());
        assertEquals("SUCCESS", event.getResult());
        assertEquals("192.168.1.100", event.getIp());
        assertEquals("Mozilla/5.0", event.getUserAgent());
        assertEquals(150, event.getDurationMs());
        assertEquals("{\"userId\": 1}", event.getRequestBody());
        assertNull(event.getErrorMessage());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 30), event.getCreateTime());
    }

    /**
     * 构建器应支持一次性设置完整审计数据。
     */
    @Test
    @DisplayName("Builder - 完整构建")
    void testBuilder() {
        AuditLogEvent event = AuditLogEvent.builder()
                .traceId("trace-456")
                .operator("user1")
                .operation("创建订单")
                .type(AuditType.BUSINESS)
                .bizNo("ORD-001")
                .result("FAIL")
                .ip("10.0.0.1")
                .durationMs(2000)
                .errorMessage("库存不足")
                .build();

        assertEquals("trace-456", event.getTraceId());
        assertEquals("user1", event.getOperator());
        assertEquals("创建订单", event.getOperation());
        assertEquals(AuditType.BUSINESS, event.getType());
        assertEquals("ORD-001", event.getBizNo());
        assertEquals("FAIL", event.getResult());
        assertEquals("10.0.0.1", event.getIp());
        assertEquals(2000, event.getDurationMs());
        assertEquals("库存不足", event.getErrorMessage());
    }

    /**
     * 最小构建方式应保留默认审计类型和创建时间。
     */
    @Test
    @DisplayName("Builder - 最小构建")
    void testBuilderMinimal() {
        AuditLogEvent event = AuditLogEvent.builder()
                .operation("简单操作")
                .build();

        assertEquals("简单操作", event.getOperation());
        assertEquals(AuditType.BUSINESS, event.getType());
        assertNull(event.getTraceId());
        assertNotNull(event.getCreateTime());
    }
}
