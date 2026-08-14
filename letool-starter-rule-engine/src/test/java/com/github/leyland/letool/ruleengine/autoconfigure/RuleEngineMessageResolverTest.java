package com.github.leyland.letool.ruleengine.autoconfigure;

import com.github.leyland.letool.exception.code.ErrorCode;
import com.github.leyland.letool.exception.core.BaseException;
import com.github.leyland.letool.exception.message.DefaultMessageResolver;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.exception.message.SpringMessageResolver;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** 规则引擎消息资源和异常模块适配边界测试。 */
class RuleEngineMessageResolverTest {

    private static final String BASENAME = "i18n/letool-rule-engine/messages";
    /** 与诊断基础文案公开格式协议一致的最大 UTF-16 长度。 */
    private static final int MAX_BASE_MESSAGE_LENGTH = 2048;
    private static final List<String> BUNDLES = List.of(
            BASENAME + ".properties",
            BASENAME + "_zh_CN.properties",
            BASENAME + "_en.properties");
    private static final Set<String> LIMIT_CODES = Set.of(
            "RULE_ENGINE_LIMIT_001",
            "RULE_ENGINE_LIMIT_002",
            "RULE_ENGINE_LIMIT_003",
            "RULE_ENGINE_LIMIT_004");
    private static final Pattern MESSAGE_FORMAT_PLACEHOLDER =
            Pattern.compile("\\{\\d+(?:,[^}]*)?}");
    private static final Map<String, String> CHINESE_MESSAGES = Map.ofEntries(
            Map.entry("RULE_ENGINE_LIMIT_001", "规则源文本超过允许限制"),
            Map.entry("RULE_ENGINE_LIMIT_002", "规则词法单元数量超过允许限制"),
            Map.entry("RULE_ENGINE_LIMIT_003", "规则表达式深度超过允许限制"),
            Map.entry("RULE_ENGINE_LIMIT_004", "函数调用次数超过允许限制"),
            Map.entry("RULE_ENGINE_API_001", "规则引擎组件注册冲突"),
            Map.entry("RULE_ENGINE_API_002", "规则引擎参数不合法"),
            Map.entry("RULE_ENGINE_COMPILE_001", "规则表达式编译失败"),
            Map.entry("RULE_ENGINE_RUNTIME_001", "规则表达式求值发生技术故障"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_001", "字符串字面量未闭合"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_002", "事实路径未闭合"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_003", "字符串包含非法转义"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_004", "规则源文本包含未知字符"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_001", "出现不符合语法位置的词法单元"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_002", "运算符缺少操作数"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_003", "表达式括号缺失或不匹配"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_004", "BETWEEN表达式缺少AND"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_005", "裸标识符不属于当前规则语法"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_006", "显式时间字面量格式无效"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_001", "事实路径语法无效"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_002", "事实契约中不存在引用路径"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_003", "函数目录中不存在引用函数"),
            Map.entry("RULE_ENGINE_FUNCTION_001", "函数参数数量不匹配"),
            Map.entry("RULE_ENGINE_FUNCTION_002", "函数参数类型不匹配"),
            Map.entry("RULE_ENGINE_FUNCTION_003", "规则函数执行失败"),
            Map.entry("RULE_ENGINE_TYPE_001", "运算符两侧类型不匹配"),
            Map.entry("RULE_ENGINE_EVALUATE_001", "求值所需事实缺失"),
            Map.entry("RULE_ENGINE_EVALUATE_002", "运行期事实类型与编译契约不一致"),
            Map.entry("RULE_ENGINE_EVALUATE_003", "编译产物指纹与当前环境不一致"),
            Map.entry("RULE_ENGINE_EVALUATE_004", "规则表达式求值失败"));
    private static final Map<String, String> ENGLISH_MESSAGES = Map.ofEntries(
            Map.entry("RULE_ENGINE_LIMIT_001", "Rule source text exceeds the allowed limit"),
            Map.entry("RULE_ENGINE_LIMIT_002", "Rule token count exceeds the allowed limit"),
            Map.entry("RULE_ENGINE_LIMIT_003", "Rule expression depth exceeds the allowed limit"),
            Map.entry("RULE_ENGINE_LIMIT_004", "Function invocation count exceeds the allowed limit"),
            Map.entry("RULE_ENGINE_API_001", "Rule engine component registration conflict"),
            Map.entry("RULE_ENGINE_API_002", "Invalid rule engine argument"),
            Map.entry("RULE_ENGINE_COMPILE_001", "Rule expression compilation failed"),
            Map.entry("RULE_ENGINE_RUNTIME_001", "Rule expression evaluation encountered a technical failure"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_001", "Unterminated string literal"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_002", "Unterminated fact path"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_003", "String contains an unsupported escape"),
            Map.entry("RULE_ENGINE_COMPILE_LEXICAL_004", "Rule source contains an unknown character"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_001", "Unexpected token"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_002", "Operator is missing an operand"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_003", "Expression parenthesis is missing or mismatched"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_004", "BETWEEN expression is missing AND"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_005", "Bare identifier is not supported by the current rule syntax"),
            Map.entry("RULE_ENGINE_COMPILE_SYNTAX_006", "Explicit temporal literal is invalid"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_001", "Fact path syntax is invalid"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_002", "Referenced fact path is absent from the fact contract"),
            Map.entry("RULE_ENGINE_COMPILE_SEMANTIC_003", "Unknown rule function"),
            Map.entry("RULE_ENGINE_FUNCTION_001", "Function argument count does not match"),
            Map.entry("RULE_ENGINE_FUNCTION_002", "Function argument type does not match"),
            Map.entry("RULE_ENGINE_FUNCTION_003", "Rule function execution failed"),
            Map.entry("RULE_ENGINE_TYPE_001", "Operator operand types do not match"),
            Map.entry("RULE_ENGINE_EVALUATE_001", "A required fact value is missing"),
            Map.entry("RULE_ENGINE_EVALUATE_002", "Runtime fact type does not match the compiled contract"),
            Map.entry("RULE_ENGINE_EVALUATE_003", "Compiled expression fingerprint does not match the current environment"),
            Map.entry("RULE_ENGINE_EVALUATE_004", "Rule expression evaluation failed"));

    /** 每个资源文件必须精确覆盖错误码与诊断码的联合空间。 */
    @Test
    void shippedBundlesExactlyCoverTheStableCodeUnion() throws IOException {
        Set<String> expectedKeys = Stream.concat(
                        Arrays.stream(RuleEngineErrorCode.values())
                                .map(RuleEngineErrorCode::getCode),
                        Arrays.stream(RuleDiagnosticCode.values())
                                .map(RuleDiagnosticCode::getCode))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(expectedKeys).hasSize(29);
        for (String resource : BUNDLES) {
            BundleData bundle = readBundle(resource);

            assertThat(bundle.keys()).as(resource + " must not contain duplicate keys")
                    .doesNotHaveDuplicates();
            assertThat(new TreeSet<>(bundle.keys())).as(resource)
                    .containsExactlyElementsOf(expectedKeys);
            assertThat(bundle.keys().stream()
                    .filter(LIMIT_CODES::contains)
                    .toList())
                    .as(resource + " shared limit keys")
                    .containsExactlyInAnyOrderElementsOf(LIMIT_CODES);
        }
    }

    /** 资源文案不得把动态参数交给 MessageFormat，也不得携带危险字符。 */
    @Test
    void shippedBundleValuesArePlainBoundedDisplayText() throws IOException {
        for (String resource : BUNDLES) {
            BundleData bundle = readBundle(resource);

            assertThat(bundle.values()).allSatisfy((key, value) -> {
                assertThat(isBundleValueSafe(value)).as(resource + ":" + key)
                        .isTrue();
                assertThat(value.length()).as(resource + ":" + key + " length")
                        .isLessThanOrEqualTo(MAX_BASE_MESSAGE_LENGTH);
            });
        }
    }

    /** 资源安全契约必须拒绝超过格式化边界一个字符的测试夹具。 */
    @Test
    void bundleValueSafetyContractRejectsOverlongFixture() {
        assertThat(isBundleValueSafe("x".repeat(MAX_BASE_MESSAGE_LENGTH + 1)))
                .isFalse();
    }

    /** 默认和简体中文资源保持中文，英文关键语义保持稳定且彼此分离。 */
    @Test
    void localizedBundlesKeepChineseDefaultsAndDistinctEnglishSemantics()
            throws IOException {
        BundleData defaults = readBundle(BASENAME + ".properties");
        BundleData chinese = readBundle(BASENAME + "_zh_CN.properties");
        BundleData english = readBundle(BASENAME + "_en.properties");

        assertThat(defaults.values().values()).allSatisfy(value ->
                assertThat(containsHanCharacter(value)).isTrue());
        assertThat(chinese.values().values()).allSatisfy(value ->
                assertThat(containsHanCharacter(value)).isTrue());
        assertThat(english.values().values()).allSatisfy(value -> {
            assertThat(containsHanCharacter(value)).isFalse();
            assertThat(containsLatinCharacter(value)).isTrue();
        });
        assertThat(defaults.values())
                .containsExactlyInAnyOrderEntriesOf(CHINESE_MESSAGES);
        assertThat(chinese.values())
                .containsExactlyInAnyOrderEntriesOf(CHINESE_MESSAGES);
        assertThat(english.values())
                .containsExactlyInAnyOrderEntriesOf(ENGLISH_MESSAGES);
    }

    /** 真实消息源按逐个枚举定义解析默认、中文和英文资源。 */
    @Test
    void springResolverResolvesEveryCodeFromAllThreeShippedBundles() {
        SpringMessageResolver resolver = springResolver(null, Locale.ENGLISH);
        DefaultMessageResolver defaultResolver = new DefaultMessageResolver(Locale.ROOT);

        for (ErrorCode code : allCodeDefinitions()) {
            String stableCode = code.getCode();
            assertThat(defaultResolver.resolve(code, Locale.ROOT)).as(stableCode + " code default")
                    .isEqualTo(code.getDefaultMessage());
            assertThat(resolver.resolve(code, Locale.ROOT)).as(stableCode + " root")
                    .isEqualTo(CHINESE_MESSAGES.get(stableCode));
            assertThat(resolver.resolve(code, Locale.SIMPLIFIED_CHINESE))
                    .as(stableCode + " zh_CN")
                    .isEqualTo(CHINESE_MESSAGES.get(stableCode));
            String english = resolver.resolve(code, Locale.ENGLISH);
            assertThat(english).as(stableCode + " en")
                    .isEqualTo(ENGLISH_MESSAGES.get(stableCode));
            assertThat(isBundleValueSafe(english)).as(stableCode + " safe en")
                    .isTrue();
        }
    }

    /** 每个诊断码都通过真实资源解析并保留精确前缀。 */
    @Test
    void adapterRendersEveryDiagnosticCodeFromEveryShippedLocale() {
        MessageResolverDiagnosticAdapter adapter =
                new MessageResolverDiagnosticAdapter(springResolver(null, Locale.ENGLISH));

        for (RuleDiagnosticCode code : RuleDiagnosticCode.values()) {
            RuleDiagnostic diagnostic = diagnostic(code, List.of());
            assertThat(adapter.resolve(diagnostic, Locale.ROOT)).as(code.getCode() + " root")
                    .isEqualTo(formatted(code, CHINESE_MESSAGES.get(code.getCode())));
            assertThat(adapter.resolve(diagnostic, Locale.SIMPLIFIED_CHINESE))
                    .as(code.getCode() + " zh_CN")
                    .isEqualTo(formatted(code, CHINESE_MESSAGES.get(code.getCode())));
            assertThat(adapter.resolve(diagnostic, Locale.ENGLISH)).as(code.getCode() + " en")
                    .isEqualTo(formatted(code, ENGLISH_MESSAGES.get(code.getCode())));
        }
        RuleDiagnostic fallback = diagnostic(RuleDiagnosticCode.MISSING_FACT_VALUE, List.of());
        assertThat(adapter.resolve(fallback, Locale.GERMAN))
                .isEqualTo(formatted(
                        RuleDiagnosticCode.MISSING_FACT_VALUE,
                        CHINESE_MESSAGES.get(RuleDiagnosticCode.MISSING_FACT_VALUE.getCode())));
        assertThat(adapter.resolve(fallback, null))
                .isEqualTo(formatted(
                        RuleDiagnosticCode.MISSING_FACT_VALUE,
                        ENGLISH_MESSAGES.get(RuleDiagnosticCode.MISSING_FACT_VALUE.getCode())));
    }

    /** 应用消息源逐码覆盖 Starter，并按原顺序各追加一次安全参数。 */
    @Test
    void applicationMessageOverridesEveryCodeAndAdapterAppendsArgumentsOnceInOrder() {
        StaticMessageSource application = new StaticMessageSource();
        CHINESE_MESSAGES.keySet().forEach(code ->
                application.addMessage(code, Locale.ENGLISH, override(code)));
        SpringMessageResolver resolver = springResolver(application, Locale.ENGLISH);
        for (ErrorCode code : allCodeDefinitions()) {
            assertThat(resolver.resolve(code, Locale.ENGLISH)).as(code.getCode())
                    .isEqualTo(override(code.getCode()));
        }

        MessageResolverDiagnosticAdapter adapter =
                new MessageResolverDiagnosticAdapter(resolver);
        for (RuleDiagnosticCode code : RuleDiagnosticCode.values()) {
            RuleDiagnostic diagnostic = diagnostic(code, List.of("firstArgument", "secondArgument"));
            String resolved = adapter.resolve(diagnostic, Locale.ENGLISH);

            assertThat(resolved).as(code.getCode()).isEqualTo(
                    formatted(code, override(code.getCode())) + "：firstArgument，secondArgument");
            assertThat(countOccurrences(resolved, "firstArgument")).isOne();
            assertThat(countOccurrences(resolved, "secondArgument")).isOne();
        }
    }

    /** 适配器不得把诊断参数提前交给异常模块的 MessageFormat。 */
    @Test
    void adapterRequestsOnlyTheBaseMessageBeforeAppendingDiagnosticArguments() {
        AtomicReference<Object[]> receivedArguments = new AtomicReference<>();
        AtomicReference<ErrorCode> receivedCode = new AtomicReference<>();
        MessageResolver resolver = codeOnlyResolver((code, locale, args) -> {
            receivedCode.set(code);
            receivedArguments.set(args);
            return "base message";
        });
        MessageResolverDiagnosticAdapter adapter =
                new MessageResolverDiagnosticAdapter(resolver);
        RuleDiagnostic diagnostic =
                diagnostic(RuleDiagnosticCode.UNKNOWN_CHARACTER, List.of("first", "second"));

        assertThat(adapter.resolve(diagnostic, Locale.ENGLISH))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_004] base message：first，second");
        assertThat(receivedCode.get()).isSameAs(RuleDiagnosticCode.UNKNOWN_CHARACTER);
        assertThat(receivedArguments.get()).isEmpty();
    }

    /** 无资源解析器仍使用诊断码自带的安全中文默认文案。 */
    @Test
    void defaultMessageResolverUsesRuleDiagnosticCodeChineseDefault() {
        MessageResolverDiagnosticAdapter adapter = new MessageResolverDiagnosticAdapter(
                new DefaultMessageResolver(Locale.ENGLISH));
        RuleDiagnostic diagnostic =
                diagnostic(RuleDiagnosticCode.INVALID_ESCAPE, List.of("\\q"));

        assertThat(adapter.resolve(diagnostic, Locale.ENGLISH))
                .isEqualTo("[RULE_ENGINE_COMPILE_LEXICAL_003] 字符串包含非法转义：\\q");
    }

    /** 不可信解析器的运行时失败统一收敛为无原因链的固定安全异常。 */
    @Test
    void hostileResolverRuntimeFailuresAreConvertedWithoutLeakingDetails() {
        String secret = "resolver-secret-value";
        List<Supplier<MessageResolver>> hostileResolvers = List.of(
                () -> codeOnlyResolver((code, locale, args) -> {
                    throw new IllegalStateException(secret);
                }),
                () -> codeOnlyResolver((code, locale, args) -> {
                    throw RuleEngineException.evaluationFailed(
                            new IllegalStateException(secret));
                }));

        for (Supplier<MessageResolver> hostileResolver : hostileResolvers) {
            MessageResolverDiagnosticAdapter adapter =
                    new MessageResolverDiagnosticAdapter(hostileResolver.get());

            assertSafeInvalidArgument(
                    () -> adapter.resolve(
                            diagnostic(RuleDiagnosticCode.UNEXPECTED_TOKEN, List.of()),
                            Locale.ENGLISH),
                    secret);
        }
    }

    /** 空、空白和超长基础文案统一收敛为固定安全异常。 */
    @Test
    void hostileResolverInvalidMessagesAreConvertedToSafeInvalidArgument() {
        String secret = "secret";
        String tooLong = secret + "x".repeat(2049 - secret.length());
        List<String> invalidMessages = Arrays.asList(null, " \t\n", tooLong);

        for (String invalidMessage : invalidMessages) {
            MessageResolverDiagnosticAdapter adapter = new MessageResolverDiagnosticAdapter(
                    codeOnlyResolver((code, locale, args) -> invalidMessage));

            assertSafeInvalidArgument(
                    () -> adapter.resolve(
                            diagnostic(RuleDiagnosticCode.UNEXPECTED_TOKEN, List.of()),
                            Locale.ENGLISH),
                    secret);
        }
    }

    /** Error 属于虚拟机和测试框架边界，不得被适配器吞掉。 */
    @Test
    void resolverErrorsPassThroughUnchanged() {
        AssertionError failure = new AssertionError("assertion-secret");
        MessageResolverDiagnosticAdapter adapter = new MessageResolverDiagnosticAdapter(
                codeOnlyResolver((code, locale, args) -> {
                    throw failure;
                }));

        AssertionError thrown = catchThrowableOfType(
                AssertionError.class,
                () -> adapter.resolve(
                        diagnostic(RuleDiagnosticCode.UNEXPECTED_TOKEN, List.of()),
                        Locale.ENGLISH));

        assertThat(thrown).isSameAs(failure);
    }

    /** 构造依赖和诊断输入为空时提供稳定边界。 */
    @Test
    void constructorAndResolveRejectNullPrimaryInputs() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MessageResolverDiagnosticAdapter(null))
                .withMessageContaining("messageResolver");

        MessageResolverDiagnosticAdapter adapter = new MessageResolverDiagnosticAdapter(
                codeOnlyResolver((code, locale, args) -> "base message"));
        assertSafeInvalidArgument(
                () -> adapter.resolve(null, Locale.ENGLISH),
                "base message");
    }

    /** 同一个适配器可被并发共享，千次解析结果必须一致。 */
    @Test
    void sharedAdapterProducesStableResultsAcrossOneThousandConcurrentCalls()
            throws Exception {
        MessageResolverDiagnosticAdapter adapter =
                new MessageResolverDiagnosticAdapter(springResolver(null, Locale.ENGLISH));
        RuleDiagnostic diagnostic =
                diagnostic(RuleDiagnosticCode.UNKNOWN_FUNCTION, List.of("sharedFunction"));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Callable<String>> tasks = Stream.generate(() ->
                        (Callable<String>) () -> adapter.resolve(diagnostic, Locale.ENGLISH))
                .limit(1000)
                .toList();

        try {
            List<Future<String>> futures = executor.invokeAll(tasks);
            assertThat(futures).hasSize(1000).allSatisfy(future ->
                    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(
                            "[RULE_ENGINE_COMPILE_SEMANTIC_003] Unknown rule function：sharedFunction"));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static SpringMessageResolver springResolver(
            StaticMessageSource application,
            Locale defaultLocale) {
        ResourceBundleMessageSource starter = new ResourceBundleMessageSource();
        starter.setBasename(BASENAME);
        starter.setDefaultEncoding(StandardCharsets.UTF_8.name());
        starter.setFallbackToSystemLocale(false);
        return new SpringMessageResolver(application, starter, defaultLocale);
    }

    private static RuleDiagnostic diagnostic(
            RuleDiagnosticCode code,
            List<Object> arguments) {
        return new RuleDiagnostic(
                code,
                DiagnosticSeverity.ERROR,
                DiagnosticPhase.SEMANTIC,
                0,
                1,
                arguments,
                null);
    }

    private static MessageResolver codeOnlyResolver(CodeResolution resolution) {
        return new MessageResolver() {
            @Override
            public String resolve(BaseException exception) {
                throw new AssertionError("unexpected BaseException overload");
            }

            @Override
            public String resolve(BaseException exception, Locale locale) {
                throw new AssertionError("unexpected BaseException overload");
            }

            @Override
            public String resolve(ErrorCode errorCode, Locale locale, Object... args) {
                return resolution.resolve(errorCode, locale, args);
            }
        };
    }

    private static void assertSafeInvalidArgument(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String secret) {
        RuleEngineException exception =
                catchThrowableOfType(RuleEngineException.class, action);

        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).doesNotContain(secret);
    }

    private static BundleData readBundle(String resource) throws IOException {
        InputStream stream = RuleEngineMessageResolverTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertThat(stream).as(resource).isNotNull();
        List<String> keys = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                int separator = line.indexOf('=');
                assertThat(separator).as(resource + " malformed line: " + line)
                        .isGreaterThan(0);
                String key = line.substring(0, separator).trim();
                keys.add(key);
            }
        }
        Properties decoded = new Properties();
        InputStream decodedStream = RuleEngineMessageResolverTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertThat(decodedStream).as(resource).isNotNull();
        try (InputStreamReader reader = new InputStreamReader(
                decodedStream, StandardCharsets.UTF_8)) {
            decoded.load(reader);
        }
        Map<String, String> decodedValues = decoded.stringPropertyNames().stream()
                .collect(Collectors.toMap(
                        key -> key,
                        decoded::getProperty,
                        (left, right) -> right,
                        LinkedHashMap::new));
        return new BundleData(keys, decodedValues);
    }

    private static boolean containsUnsafeCharacter(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                return true;
            }
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= text.length()
                        || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBundleValueSafe(String value) {
        return !value.isBlank()
                && value.length() <= MAX_BASE_MESSAGE_LENGTH
                && !MESSAGE_FORMAT_PLACEHOLDER.matcher(value).find()
                && !containsUnsafeCharacter(value);
    }

    private static List<ErrorCode> allCodeDefinitions() {
        return Stream.concat(
                        Arrays.stream(RuleEngineErrorCode.values()).map(code -> (ErrorCode) code),
                        Arrays.stream(RuleDiagnosticCode.values()).map(code -> (ErrorCode) code))
                .toList();
    }

    private static String formatted(RuleDiagnosticCode code, String baseMessage) {
        return "[" + code.getCode() + "] " + baseMessage;
    }

    private static String override(String code) {
        return "override-" + code;
    }

    private static boolean containsHanCharacter(String text) {
        return text.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static boolean containsLatinCharacter(String text) {
        return text.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
    }

    private static int countOccurrences(String text, String expected) {
        return (text.length() - text.replace(expected, "").length()) / expected.length();
    }

    @FunctionalInterface
    private interface CodeResolution {
        String resolve(ErrorCode errorCode, Locale locale, Object[] args);
    }

    private record BundleData(List<String> keys, Map<String, String> values) {
    }
}
