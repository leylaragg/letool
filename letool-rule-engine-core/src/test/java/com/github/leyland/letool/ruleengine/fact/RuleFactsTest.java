package com.github.leyland.letool.ruleengine.fact;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import java.time.Duration;

/**
 * 规则事实树的不可变性、规范化与路径解析测试。
 */
class RuleFactsTest {

    /**
     * 验证输入数据会被深层复制并映射为稳定事实类型。
     */
    @Test
    void shouldDeepCopyAndNormalizeSupportedValues() {
        List<Object> scores = new ArrayList<>(List.of(1, 2.5D));
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", new StringBuilder("unused").toString());
        customer.put("scores", scores);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("customer", customer);
        source.put("enabled", true);
        source.put("date", LocalDate.of(2026, 8, 13));
        source.put("dateTime", LocalDateTime.of(2026, 8, 13, 12, 30));
        source.put("instant", Instant.parse("2026-08-13T04:30:00Z"));
        source.put("character", 'A');
        source.put("nothing", null);
        source.put("codes", new String[]{"A", "B"});

        RuleFacts facts = RuleFacts.fromMap(source);
        scores.set(0, 99);
        customer.put("name", "changed");
        source.put("enabled", false);

        assertThat(facts.require("customer.name").kind()).isEqualTo(FactKind.STRING);
        assertThat(facts.require("customer.scores[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
        assertThat(facts.require("customer.scores[1]").toSafeJavaValue())
                .isEqualTo(new BigDecimal("2.5"));
        assertThat(facts.require("enabled").toSafeJavaValue()).isEqualTo(true);
        assertThat(facts.require("date").kind()).isEqualTo(FactKind.DATE);
        assertThat(facts.require("dateTime").kind()).isEqualTo(FactKind.DATE_TIME);
        assertThat(facts.require("instant").kind()).isEqualTo(FactKind.INSTANT);
        assertThat(facts.require("character").kind()).isEqualTo(FactKind.STRING);
        assertThat(facts.require("character").toSafeJavaValue()).isEqualTo("A");
        assertThat(facts.require("nothing")).isSameAs(NullFactValue.instance());
        assertThat(facts.require("codes[1]").toSafeJavaValue()).isEqualTo("B");
    }

    /**
     * 验证安全 Java 视图不会泄漏事实树内部集合。
     */
    @Test
    void shouldReturnUnmodifiableSafeView() {
        RuleFacts facts = RuleFacts.fromMap(Map.of(
                "customer", Map.of("scores", List.of(1, 2))));

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) facts.toSafeJavaValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) root.get("customer");
        @SuppressWarnings("unchecked")
        List<Object> scores = (List<Object>) customer.get("scores");

        assertThatThrownBy(() -> root.put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scores.set(0, BigInteger.TEN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(facts.require("customer.scores[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
    }

    /**
     * 验证相同事实树具有稳定值语义，不同字段产生不同结果。
     */
    @Test
    void shouldCompareRuleFactsByRootValue() {
        RuleFacts first = RuleFacts.fromMap(Map.of("customer", Map.of("age", 42)));
        RuleFacts equal = RuleFacts.fromMap(Map.of("customer", Map.of("age", 42)));
        RuleFacts different = RuleFacts.fromMap(Map.of("customer", Map.of("age", 43)));

        assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(first).isNotEqualTo(different);
    }

    /**
     * 验证可变 Java 数组在事实构建时被深层复制。
     */
    @Test
    void shouldSnapshotMutableJavaArray() {
        int[] source = {1, 2};

        RuleFacts facts = RuleFacts.fromMap(Map.of("numbers", source));
        source[0] = 99;

        assertThat(facts.require("numbers[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
    }

    /**
     * 验证所有整数与有限小数都使用无损规范类型。
     */
    @Test
    void shouldNormalizeNumbersWithoutExposingPrimitiveWrappers() {
        RuleFacts facts = RuleFacts.fromMap(Map.of(
                "byteValue", (byte) 1,
                "shortValue", (short) 2,
                "intValue", 3,
                "longValue", 4L,
                "bigInteger", BigInteger.valueOf(5),
                "floatValue", 1.25F,
                "doubleValue", 2.5D,
                "decimalValue", new BigDecimal("3.750")));

        assertThat(facts.require("byteValue").toSafeJavaValue()).isEqualTo(BigInteger.ONE);
        assertThat(facts.require("longValue").toSafeJavaValue()).isEqualTo(BigInteger.valueOf(4));
        assertThat(facts.require("floatValue").toSafeJavaValue()).isEqualTo(new BigDecimal("1.25"));
        assertThat(facts.require("doubleValue").toSafeJavaValue()).isEqualTo(new BigDecimal("2.5"));
        assertThat(facts.require("decimalValue").toSafeJavaValue()).isEqualTo(new BigDecimal("3.750"));
    }

    /**
     * 验证非法键、危险对象、非有限小数和循环引用均被拒绝。
     */
    @Test
    void shouldRejectUnsafeOrAmbiguousInputs() throws NoSuchMethodException {
        Map<String, Object> self = new HashMap<>();
        self.put("self", self);
        Method method = String.class.getMethod("length");

        assertInvalid(() -> RuleFacts.fromMap(singleEntryMap(null, "value")));
        assertInvalid(() -> RuleFacts.fromMap(mapWithNonStringKey(42, "value")));
        assertInvalid(() -> RuleFacts.fromMap(Map.of(" ", "value")));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("nan", Double.NaN)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("infinity", Float.POSITIVE_INFINITY)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("type", RuleFactsTest.class)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("method", method)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("thread", Thread.currentThread())));
        assertInvalid(() -> RuleFacts.fromMap(Map.of(
                "stream", new ByteArrayInputStream(new byte[0]))));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("action", (Runnable) () -> { })));
        assertInvalid(() -> RuleFacts.fromMap(self));
    }

    /**
     * 验证兄弟分支可以安全复用同一可变容器，而不会被误判为循环引用。
     */
    @Test
    void shouldAllowSharedContainerAcrossSiblingBranches() {
        List<Object> sharedList = new ArrayList<>(List.of(1));
        Map<String, Object> sharedMap = new LinkedHashMap<>();
        sharedMap.put("scores", sharedList);

        RuleFacts facts = RuleFacts.fromMap(Map.of("left", sharedMap, "right", sharedMap));
        sharedList.set(0, 99);
        sharedMap.put("extra", "changed");

        assertThat(facts.require("left.scores[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
        assertThat(facts.require("right.scores[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
        assertThat(facts.resolve("left.extra")).isEmpty();
        assertThat(facts.resolve("right.extra")).isEmpty();
        ObjectFactValue root = facts.root();
        assertThat(root.property("left")).isNotSameAs(root.property("right"));
    }

    /**
     * 验证事实解析器以规则事实作为公开入口，并区分缺失与非法遍历。
     */
    @Test
    void shouldResolveRuleFactsThroughPublicResolverApi() {
        RuleFacts facts = RuleFacts.fromMap(Map.of(
                "customer", Map.of("age", 42),
                "items", List.of(Map.of("price", "10"))));
        FactResolver resolver = new FactResolver();

        assertThat(resolver.resolve(facts, FactPathParser.parse("customer.age")))
                .containsInstanceOf(ScalarFactValue.class);
        assertThat(resolver.resolve(facts, FactPathParser.parse("customer.missing"))).isEmpty();
        assertThat(resolver.resolve(facts, FactPathParser.parse("items[2].price"))).isEmpty();
        assertInvalid(() -> resolver.resolve(facts, FactPathParser.parse("customer.age.value")));
        assertInvalid(() -> resolver.resolve(facts, FactPathParser.parse("customer[0]")));
        assertInvalid(() -> resolver.require(facts, FactPathParser.parse("customer.missing")));
    }

    @Test
    void shouldEnforceFactNormalizationBudgets() {
        assertInvalid(() -> RuleFacts.fromMap(Map.of("a", Map.of("b", 1)), limits(2, 20, 10)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("a", List.of(1, 2)), limits(10, 3, 10)));
        assertInvalid(() -> RuleFacts.fromMap(Map.of("a", List.of(1, 2)), limits(10, 20, 1)));
    }

    @Test
    void shouldCountSharedContainerReferencesWithinNodeBudget() {
        Map<String, Object> leaf = Map.of("value", 1);
        Map<String, Object> level2 = Map.of("left", leaf, "right", leaf);
        Map<String, Object> level1 = Map.of("left", level2, "right", level2);

        assertInvalid(() -> RuleFacts.fromMap(Map.of("root", level1), limits(10, 11, 10)));
        RuleFacts facts = RuleFacts.fromMap(Map.of("root", level1), limits(10, 12, 10));

        assertThat(facts.require("root.left.right.value").asBigInteger()).isEqualTo(BigInteger.ONE);
        assertThat(facts.require("root.right.left.value").asBigInteger()).isEqualTo(BigInteger.ONE);
        assertTimeoutPreemptively(Duration.ofSeconds(2), facts::toSafeJavaValue);
    }

    @Test
    void shouldBoundDeepSharedDagByExpandedTreeNodes() {
        Map<String, Object> current = Map.of("value", 1);
        for (int depth = 0; depth < 40; depth++) {
            current = Map.of("left", current, "right", current);
        }
        Map<String, Object> deepDag = current;

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertInvalid(() -> RuleFacts.fromMap(
                        Map.of("root", deepDag), limits(64, 100_000, 10))));
    }

    @Test
    void shouldCountActualCollectionItemsInsteadOfReportedSize() {
        Collection<Object> deceptive = new AbstractCollection<>() {
            @Override
            public Iterator<Object> iterator() {
                return List.<Object>of(1).iterator();
            }

            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }
        };

        RuleFacts facts = RuleFacts.fromMap(Map.of("items", deceptive), limits(10, 10, 2));

        assertThat(facts.require("items[0]").asBigInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void shouldRejectInvalidContainerFactValuesAndDefensivelyCopy() {
        Map<String, FactValue> properties = new LinkedHashMap<>();
        properties.put("value", FactValues.integer(1));
        ObjectFactValue object = new ObjectFactValue(properties);
        properties.put("value", FactValues.integer(2));
        assertThat(((Map<?, ?>) object.toSafeJavaValue()).get("value")).isEqualTo(BigInteger.ONE);

        List<FactValue> elements = new ArrayList<>(List.of(FactValues.integer(1)));
        ArrayFactValue array = new ArrayFactValue(elements);
        elements.set(0, FactValues.integer(2));
        assertThat(array.toSafeJavaValue()).isEqualTo(List.of(BigInteger.ONE));

        assertInvalid(() -> new ObjectFactValue(null));
        assertInvalid(() -> new ObjectFactValue(singleFactEntry(null, FactValues.integer(1))));
        assertInvalid(() -> new ObjectFactValue(singleFactEntry(" ", FactValues.integer(1))));
        assertInvalid(() -> new ObjectFactValue(singleFactEntry("value", null)));
        assertInvalid(() -> new ArrayFactValue(null));
        assertInvalid(() -> new ArrayFactValue(java.util.Arrays.asList(FactValues.integer(1), null)));
    }

    private static Map<String, FactValue> singleFactEntry(String key, FactValue value) {
        Map<String, FactValue> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    private static com.github.leyland.letool.ruleengine.api.EngineLimits limits(
            int depth, int nodes, int container) {
        return new com.github.leyland.letool.ruleengine.api.EngineLimits(
                100, 100, 100, 100, 100, 100, depth, nodes, container);
    }

    /**
     * 验证路径解析成功、缺失、越界和段类型不匹配的稳定行为。
     */
    @Test
    void shouldResolvePathsAndDistinguishMissingFromInvalidTraversal() {
        RuleFacts facts = RuleFacts.fromMap(Map.of(
                "customer", Map.of("age", 42),
                "items", List.of(Map.of("price", "10"))));

        assertThat(facts.resolve("${customer.age}")).containsInstanceOf(ScalarFactValue.class);
        assertThat(facts.resolve("customer.missing")).isEmpty();
        assertThat(facts.resolve("items[2].price")).isEmpty();
        assertThatThrownBy(() -> facts.resolve("customer.age.value"))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> facts.resolve("customer[0]"))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> facts.require("customer.missing"))
                .isInstanceOf(RuleEngineException.class);
        assertThatCode(() -> facts.require("customer.age").asBigInteger())
                .doesNotThrowAnyException();
        assertInvalid(() -> facts.require("items[0].price").asBigInteger());
    }

    /**
     * 构造允许空键的单项映射，用于验证外部输入校验。
     *
     * @param key 映射键
     * @param value 映射值
     * @return 可变单项映射
     */
    private static Map<String, Object> singleEntryMap(String key, Object value) {
        Map<String, Object> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    /**
     * 使用通配映射保留非法外部键，模拟无法由泛型签名约束的运行时输入。
     *
     * @param key 非字符串键
     * @param value 映射值
     * @return 伪装为字符串键的非法映射
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> mapWithNonStringKey(Object key, Object value) {
        Map raw = new HashMap();
        raw.put(key, value);
        return (Map<String, Object>) raw;
    }

    /**
     * 断言操作抛出统一的非法参数异常。
     *
     * @param operation 待执行操作
     */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }
}
