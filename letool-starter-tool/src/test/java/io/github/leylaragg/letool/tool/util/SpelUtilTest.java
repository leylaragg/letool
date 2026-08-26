package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.spel.SpelErrorCode;
import io.github.leylaragg.letool.tool.spel.SpelException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpelUtil} 的表达式求值契约测试。
 */
class SpelUtilTest {

    /**
     * 验证普通变量表达式可以按指定类型返回。
     */
    @Test
    void evalSimple() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 10);
        vars.put("y", 20);
        Integer result = SpelUtil.eval("#x + #y", null, vars, Integer.class);
        assertEquals(30, result);
    }

    /**
     * 验证表达式可以读取根对象属性。
     */
    @Test
    void evalWithRoot() {
        Map<String, Object> root = new HashMap<>();
        root.put("name", "test");
        String result = SpelUtil.evalAs("[name]", root, String.class);
        assertEquals("test", result);
    }

    /**
     * 验证旧版三参数调用在变量为空时仍能通过编译并正确执行。
     */
    @Test
    void shouldKeepNullableVariablesOverloadCompatible() {
        Map<String, Object> root = Map.of("name", "Letool");

        String result = SpelUtil.eval("[name]", root, null);

        assertEquals("Letool", result);
    }

    /**
     * 验证模板使用 Spring 原生 {@code TemplateParserContext} 语义。
     */
    @Test
    void evalTemplate() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "World");
        String result = SpelUtil.evalTemplate("Hello, #{#name}!", null, vars);
        assertEquals("Hello, World!", result);
    }

    /**
     * 验证方法表达式可以访问参数名、索引参数、目标对象和方法元数据。
     *
     * @throws NoSuchMethodException 测试方法不存在时抛出
     */
    @Test
    void evalMethodProvidesSpringMethodVariables() throws NoSuchMethodException {
        Method method = GreetingService.class.getDeclaredMethod("greet", String.class, int.class);
        GreetingService target = new GreetingService("hello");

        String result = SpelUtil.evalMethod(
                "#target.prefix + ':' + #name + ':' + #p1 + ':' + #a0 + ':' + #method.name",
                target,
                method,
                new Object[]{"leyland", 2},
                String.class
        );

        assertEquals("hello:leyland:2:leyland:greet", result);
    }

    /**
     * 验证注解常用的模板形式可以同时解析参数名和索引参数。
     *
     * @throws NoSuchMethodException 测试方法不存在时抛出
     */
    @Test
    void evalMethodTemplateResolvesNamedAndIndexedArguments() throws NoSuchMethodException {
        Method method = GreetingService.class.getDeclaredMethod("greet", String.class, int.class);

        String result = SpelUtil.evalMethodTemplate(
                "greeting:#{#name}:#{#p1}",
                new GreetingService("hello"),
                method,
                new Object[]{"leyland", 2});

        assertEquals("greeting:leyland:2", result);
    }

    /**
     * 验证受限求值允许读取数据，但拒绝 Java 类型引用。
     */
    @Test
    void evalSafeRejectsTypeReferences() {
        GreetingService root = new GreetingService("safe");

        assertEquals("safe", SpelUtil.evalSafe("prefix", root, String.class));

        SpelException exception = assertThrows(
                SpelException.class,
                () -> SpelUtil.evalSafe(
                        "T(java.lang.System).getProperty('user.home')",
                        root,
                        String.class
                )
        );
        assertEquals(SpelErrorCode.EVALUATION_FAILED, exception.getErrorCode());
    }

    /**
     * 验证表达式语法错误统一转换为工具模块异常。
     */
    @Test
    void invalidExpressionUsesUnifiedException() {
        SpelException exception = assertThrows(
                SpelException.class,
                () -> SpelUtil.eval("#value +", null, Map.of("value", 1), Integer.class)
        );

        assertEquals(SpelErrorCode.PARSE_FAILED, exception.getErrorCode());
    }

    /**
     * 表达式方法上下文测试对象。
     *
     * @param prefix 问候语前缀
     */
    private record GreetingService(String prefix) {

        /**
         * 用于提供方法签名，不参与实际执行。
         *
         * @param name  用户名称
         * @param times 问候次数
         * @return 问候内容
         */
        @SuppressWarnings("unused")
        private String greet(String name, int times) {
            return prefix + name.repeat(times);
        }
    }
}
