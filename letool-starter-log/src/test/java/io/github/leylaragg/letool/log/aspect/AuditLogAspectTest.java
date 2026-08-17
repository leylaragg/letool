package io.github.leylaragg.letool.log.aspect;

import io.github.leylaragg.letool.log.annotation.AuditLog;
import io.github.leylaragg.letool.log.audit.AuditContext;
import io.github.leylaragg.letool.log.audit.AuditContextProvider;
import io.github.leylaragg.letool.log.audit.AuditLogEvent;
import io.github.leylaragg.letool.log.audit.AuditLogService;
import io.github.leylaragg.letool.log.audit.AuditType;
import io.github.leylaragg.letool.log.trace.TraceContext;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuditLogAspect} 审计日志切面的行为测试。
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditLogAspectTest {

    /**
     * 每个测试结束后清理当前线程的链路上下文。
     */
    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    /**
     * 成功执行方法时应记录完整的成功审计事件。
     */
    @Test
    void shouldRecordSuccessfulAuditEvent() {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditContextProvider contextProvider =
                () -> new AuditContext("operator-1", "127.0.0.1", "JUnit");
        AuditedService proxy = createProxy(service, contextProvider);
        TraceContext.setTraceId("trace-success");

        String result = proxy.createOrder("ORDER-1001");

        assertThat(result).isEqualTo("created");
        assertThat(service.event).isNotNull();
        assertThat(service.event.getTraceId()).isEqualTo("trace-success");
        assertThat(service.event.getOperator()).isEqualTo("operator-1");
        assertThat(service.event.getOperation()).isEqualTo("创建订单");
        assertThat(service.event.getType()).isEqualTo(AuditType.BUSINESS);
        assertThat(service.event.getBizNo()).isEqualTo("ORDER-1001");
        assertThat(service.event.getResult()).isEqualTo("SUCCESS");
        assertThat(service.event.getIp()).isEqualTo("127.0.0.1");
        assertThat(service.event.getUserAgent()).isEqualTo("JUnit");
        assertThat(service.event.getDurationMs()).isNotNegative();
        assertThat(service.event.getRequestBody()).contains("ORDER-1001");
        assertThat(service.event.getErrorMessage()).isNull();
    }

    /**
     * 业务方法失败时应记录失败事件，并继续抛出原始异常。
     */
    @Test
    void shouldRecordFailureAndRethrowOriginalException() {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditedService proxy = createProxy(service, AuditContextProvider.empty());

        assertThatThrownBy(() -> proxy.deleteOrder("ORDER-ERROR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("删除失败");

        assertThat(service.event).isNotNull();
        assertThat(service.event.getOperation()).isEqualTo("删除订单");
        assertThat(service.event.getType()).isEqualTo(AuditType.ADMIN);
        assertThat(service.event.getBizNo()).isEqualTo("ORDER-ERROR");
        assertThat(service.event.getResult()).isEqualTo("FAIL");
        assertThat(service.event.getErrorMessage()).isEqualTo("删除失败");
    }

    /**
     * 未显式开启请求参数记录时，审计事件不应保存方法参数。
     */
    @Test
    void shouldNotRecordArgumentsByDefault() {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditedService proxy = createProxy(service, AuditContextProvider.empty());

        proxy.querySecret("secret-value");

        assertThat(service.event.getRequestBody()).isNull();
    }

    /**
     * 显式开启请求参数记录后，超长 JSON 应按注解上限截断。
     */
    @Test
    void shouldTruncateRecordedArguments() {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditedService proxy = createProxy(service, AuditContextProvider.empty());

        proxy.updateDescription("abcdefghijklmnopqrstuvwxyz");

        assertThat(service.event.getRequestBody()).hasSize(12);
    }

    /**
     * 非法 SpEL 不应中断业务方法，业务编号应安全降级为空。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldIgnoreInvalidBusinessNumberExpression(CapturedOutput output) {
        CapturingAuditLogService service = new CapturingAuditLogService();
        AuditedService proxy = createProxy(service, AuditContextProvider.empty());

        String result = proxy.invalidExpression("ORDER-1");

        assertThat(result).isEqualTo("ok");
        assertThat(service.event.getBizNo()).isNull();
        assertThat(service.event.getResult()).isEqualTo("SUCCESS");
        assertThat(output).contains("解析审计业务编号失败");
    }

    /**
     * 审计服务自身失败时，不应改变成功业务方法的返回结果。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldNotAffectBusinessWhenAuditServiceFails(CapturedOutput output) {
        AuditLogService failingService = event -> {
            throw new IllegalStateException("存储不可用");
        };
        AuditedService proxy = createProxy(failingService, AuditContextProvider.empty());

        String result = proxy.querySecret("value");

        assertThat(result).isEqualTo("value");
        assertThat(output).contains("记录审计日志失败");
    }

    /**
     * 创建带有真实 Spring AOP 代理的测试服务。
     *
     * @param auditLogService 审计日志服务
     * @param contextProvider 审计上下文提供器
     * @return 可触发审计切面的代理对象
     */
    private AuditedService createProxy(
            AuditLogService auditLogService,
            AuditContextProvider contextProvider) {
        AuditLogAspect aspect = new AuditLogAspect(
                auditLogService,
                Fastjson2JsonCodec.createDefault(),
                contextProvider);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditedService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    /**
     * 保存最近一次审计事件的测试服务。
     */
    private static final class CapturingAuditLogService implements AuditLogService {

        private AuditLogEvent event;

        /**
         * 保存切面创建的审计事件。
         *
         * @param event 审计事件
         */
        @Override
        public void record(AuditLogEvent event) {
            this.event = event;
        }
    }

    /**
     * 提供不同审计场景的测试业务服务。
     */
    static class AuditedService {

        /**
         * 模拟成功创建订单。
         *
         * @param orderNo 订单编号
         * @return 固定成功结果
         */
        @AuditLog(
                operation = "创建订单",
                type = AuditType.BUSINESS,
                bizNo = "#orderNo",
                logRequestBody = true)
        public String createOrder(String orderNo) {
            return "created";
        }

        /**
         * 模拟删除订单失败。
         *
         * @param orderNo 订单编号
         */
        @AuditLog(operation = "删除订单", type = AuditType.ADMIN, bizNo = "#orderNo")
        public void deleteOrder(String orderNo) {
            throw new IllegalStateException("删除失败");
        }

        /**
         * 模拟包含敏感参数的查询。
         *
         * @param secret 敏感参数
         * @return 原始参数
         */
        @AuditLog(operation = "查询敏感数据")
        public String querySecret(String secret) {
            return secret;
        }

        /**
         * 模拟需要截断参数的更新操作。
         *
         * @param description 超长描述
         */
        @AuditLog(operation = "更新描述", logRequestBody = true, maxBodyLength = 12)
        public void updateDescription(String description) {
            // 测试方法无需执行业务逻辑。
        }

        /**
         * 模拟配置了非法 SpEL 的审计操作。
         *
         * @param orderNo 订单编号
         * @return 固定成功结果
         */
        @AuditLog(operation = "非法表达式", bizNo = "#missing.value")
        public String invalidExpression(String orderNo) {
            return "ok";
        }
    }
}
