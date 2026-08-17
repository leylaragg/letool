package io.github.leylaragg.letool.print.spel;

import io.github.leylaragg.letool.print.xml.PrintCompilationException;
import io.github.leylaragg.letool.print.xml.expression.ExpressionCompileContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受限 SpEL 编译期安全边界测试。
 *
 * @author leyland
 */
class RestrictedSpelSecurityTest {

    /** 待测试的受限 SpEL 提供方。 */
    private final RestrictedSpelConditionExpression expression =
            new RestrictedSpelConditionExpression();

    /**
     * 验证方法、类型、Bean、变量、写入和高级集合语法均在编译期拒绝。
     */
    @Test
    void shouldRejectEverySyntaxOutsideTheAstAllowlist() {
        List<String> rejectedExpressions = List.of(
                "name.toString() == 'x'",
                "getClass() != null",
                "T(java.lang.Runtime) != null",
                "new java.lang.String('x') == 'x'",
                "@unsafeBean != null",
                "#value == true",
                "#root.enabled == true",
                "#this.enabled == true",
                "enabled = true",
                "count++ > 0",
                "count-- > 0",
                "count + 1 > 2",
                "count * 2 > 2",
                "count / 2 > 0",
                "count ^ 2 > 2",
                "name + 'x' == 'ax'",
                "score % 2 == 0",
                "#unsafe() == true",
                "enabled ? true : false",
                "enabled ?: false",
                "name matches '.*'",
                "name instanceof T(java.lang.String)",
                "{1, 2}[0] == 1",
                "{'a': 1}['a'] == 1",
                "items.![name][0] == 'x'",
                "items.?[enabled][0].enabled",
                "patient?.name == 'x'",
                "items[-1] == true",
                "result: #{enabled}");

        for (String rejected : rejectedExpressions) {
            // 每一种语法都独立编译，确保新增 Spring AST 节点不会因其他用例失败而漏测。
            assertThatThrownBy(() -> compile(rejected))
                    .as("应拒绝表达式：%s", rejected)
                    .isInstanceOf(PrintCompilationException.class);
        }
    }

    /**
     * 验证安全异常不会回显表达式正文、模拟密钥或解析器原因链。
     */
    @Test
    void shouldNotExposeRejectedExpressionOrParserDetails() {
        String secret = "secret-business-value";
        String rejected = "T(java.lang.Runtime).getRuntime().exec('" + secret + "')";

        Throwable failure = catchCompilationFailure(rejected);

        assertThat(failure)
                .isInstanceOf(PrintCompilationException.class)
                .hasMessageNotContaining(secret)
                .hasMessageNotContaining("java.lang.Runtime")
                .hasNoCause();
    }

    /**
     * 验证 Java 元数据相关名称即使出现在 JSON 中也不能成为模板读取入口。
     */
    @Test
    void shouldRejectJavaMetadataPropertyNames() {
        List<String> metadataExpressions = List.of(
                "class == 'fake'",
                "patient.class == 'fake'",
                "getClass == 'fake'",
                "classLoader == 'fake'",
                "declaringClass == 'fake'");

        for (String rejected : metadataExpressions) {
            // 名称拦截属于 AST 策略，不能依赖运行时数据是否恰好包含该 JSON 字段。
            assertThatThrownBy(() -> compile(rejected))
                    .as("应拒绝元数据属性：%s", rejected)
                    .isInstanceOf(PrintCompilationException.class);
        }
    }

    /**
     * 验证 AST 节点总数在边界内可用，超过边界立即拒绝。
     */
    @Test
    void shouldLimitTotalAstNodes() {
        compile(balancedBooleanExpression(6));

        assertThatThrownBy(() -> compile(balancedBooleanExpression(7)))
                .isInstanceOf(PrintCompilationException.class);
    }

    /**
     * 验证 AST 深度在边界内可用，超过边界立即拒绝。
     */
    @Test
    void shouldLimitAstDepth() {
        compile("!".repeat(31) + "true");

        assertThatThrownBy(() -> compile("!".repeat(32) + "true"))
                .isInstanceOf(PrintCompilationException.class);
    }

    /**
     * 验证连续属性读取和数组下标数量拥有独立上限。
     */
    @Test
    void shouldLimitPropertyChainAndArrayIndexes() {
        compile(propertyChain(32) + " == null");
        compile("items" + "[0]".repeat(16) + " == null");

        assertThatThrownBy(() -> compile(propertyChain(33) + " == null"))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile("items" + "[0]".repeat(17) + " == null"))
                .isInstanceOf(PrintCompilationException.class);
    }

    /**
     * 验证字符串和数字字面量在精确边界内可用，超限时拒绝。
     */
    @Test
    void shouldLimitLiteralSizes() {
        String stringAtLimit = "x".repeat(1_024);
        String numberAtLimit = "0." + "0".repeat(61) + "1";
        compile("'" + stringAtLimit + "' == '" + stringAtLimit + "'");
        compile(numberAtLimit + " == 0");

        assertThatThrownBy(() -> compile("'" + "x".repeat(1_025) + "' == 'x'"))
                .isInstanceOf(PrintCompilationException.class);
        assertThatThrownBy(() -> compile("0." + "0".repeat(62) + "1 == 0"))
                .isInstanceOf(PrintCompilationException.class);
    }

    /**
     * 验证直接调用表达式 SPI 时也不能用超长空白正文绕过 XML 长度治理。
     */
    @Test
    void shouldLimitExpressionSourceLength() {
        compile(" ".repeat(4_092) + "true");

        assertThatThrownBy(() -> compile(" ".repeat(4_093) + "true"))
                .isInstanceOf(PrintCompilationException.class);
    }

    /**
     * 验证 Spring 解析器执行前就拒绝接近正文上限的深层括号，避免解析线程栈被耗尽。
     */
    @Test
    void shouldRejectDeepDelimitersBeforeSpringParsing() {
        List<String> deeplyNestedExpressions = List.of(
                "(".repeat(2_000) + "secret-business-value == null"
                        + ")".repeat(2_000),
                "[".repeat(2_000) + "secret-business-value"
                        + "]".repeat(2_000),
                "{".repeat(2_000) + "secret-business-value"
                        + "}".repeat(2_000));

        for (String deeplyNested : deeplyNestedExpressions) {
            // 三类分隔符分别验证，防止新增语法入口绕过解析前的统一栈深治理。
            Throwable failure = catchCompilationFailure(deeplyNested);
            assertThat(failure)
                    .isInstanceOf(PrintCompilationException.class)
                    .hasMessageNotContaining("secret-business-value")
                    .hasNoCause();
        }
    }

    /**
     * 验证解析前扫描器正确忽略字符串字面量中的括号和转义引号。
     */
    @Test
    void shouldIgnoreDelimitersInsideStringLiterals() {
        compile("'((' == '((' && \"a]]\" == \"a]]\""
                + " && 'it''s {{safe}}' == 'it''s {{safe}}'");
    }

    /**
     * 编译测试表达式。
     *
     * @param source 表达式正文
     */
    private void compile(String source) {
        expression.compile(new ExpressionCompileContext(
                "spel", source, "安全测试位置"));
    }

    /**
     * 捕获指定表达式的编译失败，便于断言公开异常边界。
     *
     * @param source 表达式正文
     * @return 捕获到的编译异常
     */
    private Throwable catchCompilationFailure(String source) {
        try {
            compile(source);
        } catch (Throwable failure) {
            return failure;
        }
        throw new AssertionError("表达式应当编译失败");
    }

    /**
     * 构造节点数量可预测且深度较低的平衡布尔表达式。
     *
     * @param depth 二叉运算层数
     * @return 平衡布尔表达式正文
     */
    private String balancedBooleanExpression(int depth) {
        if (depth == 0) {
            return "true";
        }
        String child = balancedBooleanExpression(depth - 1);
        return "(" + child + " || " + child + ")";
    }

    /**
     * 构造指定属性数量的连续读取链。
     *
     * @param propertyCount 属性节点数量
     * @return 连续属性读取正文
     */
    private String propertyChain(int propertyCount) {
        return "p" + ".p".repeat(propertyCount - 1);
    }
}
