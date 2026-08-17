package io.github.leylaragg.letool.log.aspect;

import io.github.leylaragg.letool.log.annotation.MethodLog;
import io.github.leylaragg.letool.log.config.LogProperties;
import io.github.leylaragg.letool.log.trace.TraceContext;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MethodLogAspect} 方法日志切面行为测试。
 */
@ExtendWith(OutputCaptureExtension.class)
class MethodLogAspectTest {

    /**
     * 每个测试结束后清理当前线程的链路上下文。
     */
    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    /**
     * 未记录入参与出参时，仍应输出自定义标题、成功状态和执行耗时。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldLogTitleStatusAndDurationWithoutArgumentsOrResult(CapturedOutput output) {
        SampleService proxy = createProxy(new MethodLogAspect());

        String result = proxy.createOrder("ORDER-1001");

        assertThat(result).isEqualTo("created");
        assertThat(output)
                .contains("创建订单")
                .contains("执行成功")
                .contains("耗时");
    }

    /**
     * 切面临时生成的 TraceId 应在方法结束后清理，避免污染复用当前线程的后续任务。
     */
    @Test
    void shouldClearTraceIdCreatedByAspect() {
        SampleService proxy = createProxy(new MethodLogAspect());

        proxy.createOrder("ORDER-1001");

        assertThat(TraceContext.getTraceId()).isNull();
    }

    /**
     * 业务方法失败时应记录标题和完整异常堆栈，并继续抛出原始异常。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldLogExceptionStackAndRethrowOriginalException(CapturedOutput output) {
        SampleService proxy = createProxy(new MethodLogAspect());

        assertThatThrownBy(() -> proxy.cancelOrder("ORDER-ERROR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("取消失败");

        assertThat(output)
                .contains("取消订单")
                .contains("执行失败")
                .contains("java.lang.IllegalStateException: 取消失败");
    }

    /**
     * 切面应提供依赖注入构造器，以便自动配置传入用户自定义的 JSON 编解码器。
     */
    @Test
    void shouldProvideJsonCodecInjectionConstructor() {
        assertThatCode(() -> MethodLogAspect.class.getConstructor(
                JsonCodec.class,
                LogProperties.class))
                .doesNotThrowAnyException();
    }

    /**
     * 显式开启入参与出参记录时，应使用用户提供的 JSON 编解码器完成序列化。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldSerializeArgumentsAndResultWithCustomJsonCodec(CapturedOutput output) {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.write(any())).thenAnswer(invocation ->
                invocation.getArgument(0) instanceof Object[]
                        ? "custom-json-args"
                        : "custom-json-result");
        SampleService proxy = createProxy(new MethodLogAspect(
                jsonCodec,
                new LogProperties()));

        LoggedResult result = proxy.queryOrder("ORDER-1001");

        assertThat(result.value()).isEqualTo("created");
        assertThat(output)
                .contains("custom-json-args")
                .contains("custom-json-result");
    }

    /**
     * JSON 序列化失败时不应中断业务方法，应使用安全占位文本继续记录执行结果。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldNotAffectBusinessWhenJsonSerializationFails(CapturedOutput output) {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.write(any()))
                .thenThrow(new IllegalStateException("codec unavailable"));
        SampleService proxy = createProxy(new MethodLogAspect(
                jsonCodec,
                new LogProperties()));

        LoggedResult result = proxy.queryOrder("ORDER-1001");

        assertThat(result.value()).isEqualTo("created");
        assertThat(output)
                .contains("序列化方法日志数据失败")
                .contains("<序列化失败>");
    }

    /**
     * 入参与出参应分别使用注解声明的最大长度执行截断。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldApplyIndependentArgumentAndResultLengthLimits(CapturedOutput output) {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.write(any())).thenAnswer(invocation ->
                invocation.getArgument(0) instanceof Object[]
                        ? "abcdefghijklmnop"
                        : "1234567890123456");
        SampleService proxy = createProxy(new MethodLogAspect(
                jsonCodec,
                new LogProperties()));

        proxy.queryLongOrder("ORDER-1001");

        assertThat(output)
                .contains("入参: abcdefgh...")
                .contains("出参: 1234567890...");
    }

    /**
     * 关闭链路追踪时，方法日志不应绕过配置生成新的 TraceId。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldNotGenerateTraceIdWhenTracingIsDisabled(CapturedOutput output) {
        LogProperties properties = new LogProperties();
        properties.getTrace().setEnabled(false);
        SampleService proxy = createProxy(new MethodLogAspect(
                mock(JsonCodec.class),
                properties));

        proxy.createOrder("ORDER-1001");

        assertThat(output)
                .contains("[-]")
                .doesNotContainPattern("\\[[0-9a-f]{16}]");
        assertThat(TraceContext.getTraceId()).isNull();
    }

    /**
     * 使用 JDK 接口代理时，应从目标实现方法解析注解并正常记录日志。
     *
     * @param output 当前测试捕获的日志输出
     */
    @Test
    void shouldResolveAnnotationFromImplementationBehindJdkProxy(CapturedOutput output) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new InterfaceSampleService());
        factory.setInterfaces(OrderOperations.class);
        factory.setProxyTargetClass(false);
        factory.addAspect(new MethodLogAspect());
        OrderOperations proxy = factory.getProxy();

        String result = proxy.submit("ORDER-1001");

        assertThat(result).isEqualTo("submitted");
        assertThat(output).contains("接口代理订单");
    }

    /**
     * 创建带有真实 Spring AOP 代理的测试服务。
     *
     * @param aspect 待测试的方法日志切面
     * @return 可触发方法日志切面的代理对象
     */
    private SampleService createProxy(MethodLogAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    /**
     * 提供方法日志测试场景。
     */
    static class SampleService {

        /**
         * 模拟创建订单。
         *
         * @param orderNo 订单编号
         * @return 固定创建结果
         */
        @MethodLog("创建订单")
        public String createOrder(String orderNo) {
            return "created";
        }

        /**
         * 模拟取消订单失败。
         *
         * @param orderNo 订单编号
         */
        @MethodLog("取消订单")
        public void cancelOrder(String orderNo) {
            throw new IllegalStateException("取消失败");
        }

        /**
         * 模拟显式记录入参与出参的订单查询。
         *
         * @param orderNo 订单编号
         * @return 固定查询结果
         */
        @MethodLog(value = "查询订单", logArgs = true, logResult = true)
        public LoggedResult queryOrder(String orderNo) {
            return new LoggedResult("created");
        }

        /**
         * 模拟需要分别截断入参与出参的订单查询。
         *
         * @param orderNo 订单编号
         * @return 固定查询结果
         */
        @MethodLog(
                value = "查询长订单",
                logArgs = true,
                logResult = true,
                maxArgsLength = 8,
                maxResultLength = 10)
        public LoggedResult queryLongOrder(String orderNo) {
            return new LoggedResult("created");
        }
    }

    /**
     * 提供不会与自定义 JSON 文本相同的测试返回值。
     *
     * @param value 返回值内容
     */
    private record LoggedResult(String value) {
    }

    /**
     * 提供 JDK 动态代理使用的业务接口。
     */
    interface OrderOperations {

        /**
         * 提交订单。
         *
         * @param orderNo 订单编号
         * @return 提交结果
         */
        String submit(String orderNo);
    }

    /**
     * 在实现方法上声明日志注解的接口实现。
     */
    static class InterfaceSampleService implements OrderOperations {

        /**
         * 提交订单。
         *
         * @param orderNo 订单编号
         * @return 固定提交结果
         */
        @Override
        @MethodLog("接口代理订单")
        public String submit(String orderNo) {
            return "submitted";
        }
    }
}
