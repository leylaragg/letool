package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 只读函数注册表、生命周期和目录摘要测试。
 */
class FunctionRegistryTest {

    private static final TypeDescriptor STRING = TypeDescriptor.scalar(TypeKind.STRING, false);
    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private static final FunctionCharacteristics THREAD_SAFE = FunctionCharacteristics.of(
            FunctionDeterminism.DETERMINISTIC,
            FunctionEffect.PURE,
            FunctionThreading.THREAD_SAFE);
    private static final FunctionCharacteristics INVOCATION_SCOPED = FunctionCharacteristics.of(
            FunctionDeterminism.DETERMINISTIC,
            FunctionEffect.CONTEXTUAL,
            FunctionThreading.INVOCATION_SCOPED);

    /**
     * 验证函数编码使用 Locale ROOT 大写规范化，不受土耳其语区域影响。
     */
    @Test
    void shouldNormalizeFunctionCodeWithLocaleRoot() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            FunctionRegistry registry = FunctionRegistry.builder()
                    .register(function("identity", "1.0.0", oneStringArgument(), THREAD_SAFE))
                    .build();

            assertThat(registry.requireDescriptor("identity").code()).isEqualTo("IDENTITY");
            assertThat(registry.requireDescriptor("IDENTITY").code()).isEqualTo("IDENTITY");
        } finally {
            Locale.setDefault(previous);
        }
    }

    /**
     * 验证函数编码只接受有限长度 ASCII，且超长输入会快速失败。
     */
    @Test
    void shouldRejectNonAsciiAndOversizedFunctionCodesQuickly() {
        assertInvalid(() -> FunctionDescriptor.of(
                "ſ", "1", FunctionSignature.empty(), INTEGER, THREAD_SAFE));
        assertInvalid(() -> FunctionDescriptor.of(
                "A".repeat(129), "1", FunctionSignature.empty(), INTEGER, THREAD_SAFE));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertInvalid(() -> FunctionDescriptor.of(
                        "A".repeat(1_000_000), "1", FunctionSignature.empty(), INTEGER, THREAD_SAFE)));
    }

    /**
     * 验证不可信函数元数据抛出的异常不会跨注册边界泄漏原因链或敏感文本。
     */
    @Test
    void shouldSanitizeExceptionsFromFunctionMetadataGetters() {
        RuleFunction leakingFunction = new RuleFunction() {
            @Override
            public String code() {
                throw RuleEngineException.evaluationFailed(new IllegalStateException("secret-metadata"));
            }

            @Override public String semanticVersion() { return "1"; }
            @Override public FunctionSignature signature() { return FunctionSignature.empty(); }
            @Override public TypeDescriptor returnType() { return INTEGER; }
            @Override public FunctionCharacteristics characteristics() { return THREAD_SAFE; }
            @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
                return FactValues.integer(1);
            }
        };

        assertSanitizedInvalid(() -> FunctionRegistry.builder().register(leakingFunction),
                "secret-metadata");
    }

    /**
     * 验证工厂描述符和创建方法抛出的异常均在信任边界被净化。
     */
    @Test
    void shouldSanitizeExceptionsFromFactoryBoundaries() {
        RuleFunctionFactory leakingDescriptorFactory = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                throw RuleEngineException.evaluationFailed(
                        new IllegalStateException("secret-descriptor"));
            }

            @Override public RuleFunction create() { throw new AssertionError("不得调用"); }
        };
        assertSanitizedInvalid(
                () -> FunctionRegistry.builder().register(leakingDescriptorFactory),
                "secret-descriptor");

        FunctionDescriptor descriptor = descriptor(
                "LEAK", "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        RuleFunctionFactory leakingCreateFactory = new RuleFunctionFactory() {
            @Override public FunctionDescriptor descriptor() { return descriptor; }
            @Override
            public RuleFunction create() {
                throw RuleEngineException.evaluationFailed(
                        new IllegalStateException("secret-create"));
            }
        };
        assertSanitizedInvalid(
                () -> FunctionRegistry.builder().register(leakingCreateFactory),
                "secret-create");
    }

    /**
     * 验证共享函数只注册一次实例，并为每次调用返回同一实例。
     */
    @Test
    void shouldAcquireSharedThreadSafeInstance() {
        RuleFunction function = function("LENGTH", "1", oneStringArgument(), THREAD_SAFE);
        FunctionRegistry registry = FunctionRegistry.builder().register(function).build();

        assertThat(registry.acquireForInvocation("length")).isSameAs(function);
        assertThat(registry.acquireForInvocation("LENGTH")).isSameAs(function);
    }

    /**
     * 验证调用级工厂在注册时探测一次，查询描述符不实例化，调用时才创建新实例。
     */
    @Test
    void shouldProbeFactoryAndCreateInvocationScopedInstancesLazily() {
        AtomicInteger creations = new AtomicInteger();
        FunctionDescriptor descriptor = descriptor(
                "COUNTER", "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        RuleFunctionFactory factory = factory(descriptor, creations);

        FunctionRegistry registry = FunctionRegistry.builder().register(factory).build();
        assertThat(creations).hasValue(1);
        assertThat(registry.requireDescriptor("counter")).isEqualTo(descriptor);
        assertThat(creations).hasValue(1);

        RuleFunction first = registry.acquireForInvocation("COUNTER");
        RuleFunction second = registry.acquireForInvocation("counter");
        assertThat(first).isNotSameAs(second);
        assertThat(creations).hasValue(3);
    }

    /**
     * 验证注册表会串行访问可能有状态的调用级工厂，同时仍返回独立实例。
     *
     * @throws Exception 并发任务执行失败时抛出
     */
    @Test
    void shouldSafelyAcquireInvocationScopedFunctionsConcurrently() throws Exception {
        AtomicInteger activeCreations = new AtomicInteger();
        AtomicInteger maximumActiveCreations = new AtomicInteger();
        FunctionDescriptor descriptor = descriptor(
                "COUNTER", "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        RuleFunctionFactory factory = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                int active = activeCreations.incrementAndGet();
                maximumActiveCreations.accumulateAndGet(active, Math::max);
                try {
                    Thread.sleep(20);
                    return function("COUNTER", "1", FunctionSignature.empty(), INVOCATION_SCOPED);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    activeCreations.decrementAndGet();
                }
            }
        };
        FunctionRegistry registry = FunctionRegistry.builder().register(factory).build();
        activeCreations.set(0);
        maximumActiveCreations.set(0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RuleFunction>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return registry.acquireForInvocation("COUNTER");
                }));
            }
            start.countDown();
            List<RuleFunction> instances = new java.util.ArrayList<>();
            for (Future<RuleFunction> future : futures) {
                instances.add(future.get(3, TimeUnit.SECONDS));
            }

            assertThat(maximumActiveCreations).hasValue(1);
            assertThat(instances).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 验证不同注册项使用独立锁，可以同时创建调用级函数实例。
     *
     * @throws Exception 并发任务执行失败时抛出
     */
    @Test
    void shouldCreateDifferentInvocationScopedFunctionsInParallel() throws Exception {
        AtomicInteger activeCreations = new AtomicInteger();
        AtomicInteger maximumActiveCreations = new AtomicInteger();
        CountDownLatch bothEntered = new CountDownLatch(2);
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(parallelFactory("FIRST", activeCreations, maximumActiveCreations, bothEntered))
                .register(parallelFactory("SECOND", activeCreations, maximumActiveCreations, bothEntered))
                .build();
        activeCreations.set(0);
        maximumActiveCreations.set(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            FunctionRegistry concurrentRegistry = registry;
            Future<RuleFunction> first = executor.submit(
                    () -> concurrentRegistry.acquireForInvocation("FIRST"));
            Future<RuleFunction> second = executor.submit(
                    () -> concurrentRegistry.acquireForInvocation("SECOND"));

            assertThat(first.get(3, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(3, TimeUnit.SECONDS)).isNotNull();
            assertThat(maximumActiveCreations).hasValue(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * 验证工厂探测实例的全部公开元数据必须与描述符一致。
     */
    @Test
    void shouldRejectFactoryWhoseProbeMetadataDoesNotMatchDescriptor() {
        FunctionDescriptor descriptor = descriptor(
                "COUNTER", "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        RuleFunctionFactory mismatched = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                return function("COUNTER", "2", FunctionSignature.empty(), INVOCATION_SCOPED);
            }
        };

        assertInvalid(() -> FunctionRegistry.builder().register(mismatched));
    }

    /**
     * 验证工厂描述符中的非规范编码与探测实例可按规范形式匹配。
     */
    @Test
    void shouldAcceptEquivalentNormalizedFactoryMetadata() {
        FunctionCharacteristics characteristics = FunctionCharacteristics.of(
                FunctionDeterminism.DETERMINISTIC,
                FunctionEffect.PURE,
                FunctionThreading.INVOCATION_SCOPED);
        FunctionDescriptor descriptor = descriptor(
                "identity", "1", oneStringArgument(), INTEGER, characteristics);
        RuleFunctionFactory factory = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                return function("IDENTITY", "1", oneStringArgument(), characteristics);
            }
        };

        FunctionRegistry registry = FunctionRegistry.builder().register(factory).build();
        assertThat(registry.requireDescriptor("IDENTITY").code()).isEqualTo("IDENTITY");
    }

    /**
     * 验证工厂后续创建的实例若元数据漂移也会被拒绝。
     */
    @Test
    void shouldRejectInvocationInstanceWhoseMetadataDriftsAfterProbe() {
        FunctionDescriptor descriptor = descriptor(
                "COUNTER", "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        AtomicInteger creations = new AtomicInteger();
        RuleFunctionFactory driftingFactory = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                int number = creations.incrementAndGet();
                return function("COUNTER", number == 1 ? "1" : "2",
                        FunctionSignature.empty(), INVOCATION_SCOPED);
            }
        };
        FunctionRegistry registry = FunctionRegistry.builder().register(driftingFactory).build();

        assertInvalid(() -> registry.acquireForInvocation("COUNTER"));
    }

    /**
     * 验证生命周期注册入口不能混用。
     */
    @Test
    void shouldRejectWrongRegistrationModeForThreadingCharacteristic() {
        assertInvalid(() -> FunctionRegistry.builder().register(
                function("COUNTER", "1", FunctionSignature.empty(), INVOCATION_SCOPED)));

        FunctionDescriptor threadSafeDescriptor = descriptor(
                "LENGTH", "1", oneStringArgument(), INTEGER, THREAD_SAFE);
        assertInvalid(() -> FunctionRegistry.builder().register(
                factory(threadSafeDescriptor, new AtomicInteger())));
    }

    /**
     * 验证大小写规范化后的重复编码被作为注册冲突拒绝。
     */
    @Test
    void shouldRejectDuplicateNormalizedCode() {
        FunctionRegistry.Builder builder = FunctionRegistry.builder()
                .register(function("length", "1", oneStringArgument(), THREAD_SAFE));

        assertThatThrownBy(() -> builder.register(
                function("LENGTH", "2", oneStringArgument(), THREAD_SAFE)))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.REGISTRATION_CONFLICT));
    }

    /**
     * 验证重复工厂编码在执行探测实例创建之前被拒绝。
     */
    @Test
    void shouldRejectDuplicateFactoryWithoutCreatingProbe() {
        FunctionRegistry.Builder builder = FunctionRegistry.builder()
                .register(function("COUNTER", "1", FunctionSignature.empty(), THREAD_SAFE));
        AtomicInteger creations = new AtomicInteger();
        FunctionDescriptor descriptor = descriptor(
                "counter", "2", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);

        assertThatThrownBy(() -> builder.register(factory(descriptor, creations)))
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.REGISTRATION_CONFLICT));
        assertThat(creations).hasValue(0);
    }

    /**
     * 验证目录摘要不依赖注册顺序或实例身份。
     */
    @Test
    void shouldProduceStableDigestAcrossOrderAndInstances() {
        FunctionRegistry first = FunctionRegistry.builder()
                .register(function("LENGTH", "1", oneStringArgument(), THREAD_SAFE))
                .register(function("NOW", "2", FunctionSignature.empty(),
                        characteristics(FunctionDeterminism.NON_DETERMINISTIC,
                                FunctionEffect.CONTEXTUAL, FunctionThreading.THREAD_SAFE)))
                .build();
        FunctionRegistry reordered = FunctionRegistry.builder()
                .register(function("NOW", "2", FunctionSignature.empty(),
                        characteristics(FunctionDeterminism.NON_DETERMINISTIC,
                                FunctionEffect.CONTEXTUAL, FunctionThreading.THREAD_SAFE)))
                .register(function("LENGTH", "1", oneStringArgument(), THREAD_SAFE))
                .build();

        assertThat(first.catalogDigest()).isEqualTo(reordered.catalogDigest());
        assertThat(first.catalogDigest()).matches("[0-9a-f]{64}");
    }

    /**
     * 验证长度前缀序列化能区分简单拼接会混淆的参数边界。
     */
    @Test
    void shouldSeparateDigestFieldBoundaries() {
        FunctionRegistry first = registry(function(
                "BOUNDARY", "1", FunctionSignature.of(
                        FunctionParameter.required("A", STRING),
                        FunctionParameter.required("BC", STRING)), THREAD_SAFE));
        FunctionRegistry second = registry(function(
                "BOUNDARY", "1", FunctionSignature.of(
                        FunctionParameter.required("AB", STRING),
                        FunctionParameter.required("C", STRING)), THREAD_SAFE));

        assertThat(first.catalogDigest()).isNotEqualTo(second.catalogDigest());
    }

    /**
     * 验证一个稳定目录具有固定黄金摘要。
     */
    @Test
    void shouldMatchGoldenDigest() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(function("LENGTH", "1.0.0", oneStringArgument(), THREAD_SAFE))
                .build();

        assertThat(registry.catalogDigest())
                .isEqualTo("438279e835745bf42701281acf9a11ccf6573228c8fb35bafd59106446510885");
    }

    /**
     * 验证签名、版本、返回类型和每项函数特征都会影响目录摘要。
     */
    @Test
    void shouldIncludeAllSemanticMetadataInDigest() {
        FunctionRegistry baseline = registry(function(
                "LENGTH", "1", oneStringArgument(), THREAD_SAFE));

        assertDifferentDigest(baseline, function(
                "SIZE", "1", oneStringArgument(), THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "2", oneStringArgument(), THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", FunctionSignature.of(
                        FunctionParameter.optional("value", STRING)), THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", FunctionSignature.of(
                        FunctionParameter.varargs("value", STRING)), THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", FunctionSignature.of(
                        FunctionParameter.required("text", STRING)), THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", FunctionSignature.of(
                        FunctionParameter.required("value", INTEGER)), THREAD_SAFE));
        TypeDescriptor string = TypeDescriptor.scalar(TypeKind.STRING, false);
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", oneStringArgument(),
                TypeDescriptor.array(TypeDescriptor.array(string, true), false), THREAD_SAFE));
        FunctionRegistry nestedOuterNullable = registry(function(
                "LENGTH", "1", oneStringArgument(),
                TypeDescriptor.array(TypeDescriptor.array(string, false), true), THREAD_SAFE));
        assertThat(registry(function(
                "LENGTH", "1", oneStringArgument(),
                TypeDescriptor.array(TypeDescriptor.array(string, true), false), THREAD_SAFE))
                .catalogDigest()).isNotEqualTo(nestedOuterNullable.catalogDigest());
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", oneStringArgument(), STRING, THREAD_SAFE));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", oneStringArgument(),
                characteristics(FunctionDeterminism.NON_DETERMINISTIC,
                        FunctionEffect.PURE, FunctionThreading.THREAD_SAFE)));
        assertDifferentDigest(baseline, function(
                "LENGTH", "1", oneStringArgument(),
                characteristics(FunctionDeterminism.DETERMINISTIC,
                        FunctionEffect.CONTEXTUAL, FunctionThreading.THREAD_SAFE)));
        assertDifferentDigest(baseline, functionFactoryRegistryDescriptor(
                "LENGTH", "1", oneStringArgument(), INTEGER,
                characteristics(FunctionDeterminism.DETERMINISTIC,
                        FunctionEffect.PURE, FunctionThreading.INVOCATION_SCOPED)));
    }

    /**
     * 验证描述符、参数、上下文和参数列表均执行防御复制。
     */
    @Test
    void shouldExposeOnlyImmutableFunctionInputsAndContext() {
        FactValue value = FactValues.string("A");
        FunctionArguments arguments = FunctionArguments.of(List.of(value));
        RuleFacts facts = RuleFacts.fromMap(Map.of("value", "A"));
        Map<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("invocationId", "i-1");
        FunctionContext context = FunctionContext.of(
                facts, Locale.CHINA, ZoneId.of("Asia/Shanghai"), metadata);
        metadata.put("invocationId", "changed");

        assertThat(arguments.size()).isOne();
        assertThat(arguments.get(0)).isSameAs(value);
        assertThat(arguments.values()).isUnmodifiable();
        assertThat(context.facts()).isSameAs(facts);
        assertThat(context.locale()).isEqualTo(Locale.CHINA);
        assertThat(context.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(context.invocationMetadata()).containsEntry("invocationId", "i-1").isUnmodifiable();
    }

    /**
     * 验证函数参数和上下文按全部字段提供值语义。
     */
    @Test
    void shouldCompareArgumentsAndContextByValue() {
        FunctionArguments arguments = FunctionArguments.of(FactValues.string("A"));
        FunctionArguments equalArguments = FunctionArguments.of(FactValues.string("A"));
        FunctionArguments differentArguments = FunctionArguments.of(FactValues.string("B"));
        RuleFacts facts = RuleFacts.fromMap(Map.of("value", "A"));
        FunctionContext context = FunctionContext.of(
                facts, Locale.CHINA, ZoneId.of("Asia/Shanghai"), Map.of("id", "1"));
        FunctionContext equalContext = FunctionContext.of(
                RuleFacts.fromMap(Map.of("value", "A")), Locale.CHINA,
                ZoneId.of("Asia/Shanghai"), Map.of("id", "1"));

        assertThat(arguments).isEqualTo(equalArguments).hasSameHashCodeAs(equalArguments);
        assertThat(arguments).isNotEqualTo(differentArguments);
        assertThat(context).isEqualTo(equalContext).hasSameHashCodeAs(equalContext);
        assertThat(context).isNotEqualTo(FunctionContext.of(
                RuleFacts.fromMap(Map.of("value", "B")), Locale.CHINA,
                ZoneId.of("Asia/Shanghai"), Map.of("id", "1")));
        assertThat(context).isNotEqualTo(FunctionContext.of(
                facts, Locale.US, ZoneId.of("Asia/Shanghai"), Map.of("id", "1")));
        assertThat(context).isNotEqualTo(FunctionContext.of(
                facts, Locale.CHINA, ZoneId.of("UTC"), Map.of("id", "1")));
        assertThat(context).isNotEqualTo(FunctionContext.of(
                facts, Locale.CHINA, ZoneId.of("Asia/Shanghai"), Map.of("id", "2")));
    }

    /**
     * 验证函数参数列表最多接受二百五十六个事实值。
     */
    @Test
    void shouldBoundFunctionArgumentsAtTwoHundredFiftySixValues() {
        List<FactValue> values = new java.util.ArrayList<>();
        for (int index = 0; index < 257; index++) values.add(FactValues.nullValue());

        assertThat(FunctionArguments.of(values.subList(0, 256)).size()).isEqualTo(256);
        assertInvalid(() -> FunctionArguments.of(values));
        assertInvalid(() -> FunctionArguments.of(values.toArray(FactValue[]::new)));
    }

    /**
     * 验证函数上下文元数据的项数和文本长度均受固定上限约束。
     */
    @Test
    void shouldBoundFunctionContextMetadata() {
        Map<String, String> accepted = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 64; index++) accepted.put("k" + index, "v");
        accepted.remove("k0");
        accepted.put("K".repeat(128), "V".repeat(4096));
        RuleFacts facts = RuleFacts.fromMap(Map.of());

        assertThat(FunctionContext.of(facts, Locale.ROOT, ZoneId.of("UTC"), accepted)
                .invocationMetadata()).hasSize(64);
        Map<String, String> tooMany = new java.util.LinkedHashMap<>(accepted);
        tooMany.put("overflow", "v");
        assertInvalid(() -> FunctionContext.of(
                facts, Locale.ROOT, ZoneId.of("UTC"), tooMany));
        assertInvalid(() -> FunctionContext.of(
                facts, Locale.ROOT, ZoneId.of("UTC"), Map.of("K".repeat(129), "v")));
        assertInvalid(() -> FunctionContext.of(
                facts, Locale.ROOT, ZoneId.of("UTC"), Map.of("key", "V".repeat(4097))));
    }

    /**
     * 验证元数据访问预算按实际迭代项计数，重复键不能绕过六十四项上限。
     */
    @Test
    void shouldCountVisitedMetadataEntriesEvenWhenKeysRepeat() {
        Map<String, String> repeatedKeys = metadataMap(65, entry ->
                new AbstractMap.SimpleImmutableEntry<>("same", "v"));

        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertInvalid(() -> FunctionContext.of(
                        RuleFacts.fromMap(Map.of()), Locale.ROOT,
                        ZoneId.of("UTC"), repeatedKeys)));
    }

    /**
     * 验证元数据项、键和值访问异常均会被净化。
     */
    @Test
    void shouldSanitizeMetadataEntryKeyAndValueFailures() {
        Map<String, String> leakingEntry = new AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return new java.util.AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, String>> iterator() {
                        return new Iterator<>() {
                            @Override public boolean hasNext() { return true; }
                            @Override public Entry<String, String> next() {
                                throw new IllegalStateException("secret-metadata-entry");
                            }
                        };
                    }
                    @Override public int size() { return 1; }
                };
            }
        };
        Map<String, String> leakingKey = metadataMap(1, entry ->
                new AbstractMap.SimpleImmutableEntry<>("key", "value") {
                    @Override public String getKey() {
                        throw new IllegalStateException("secret-metadata-key");
                    }
                });
        Map<String, String> leakingValue = metadataMap(1, entry ->
                new AbstractMap.SimpleImmutableEntry<>("key", "value") {
                    @Override public String getValue() {
                        throw new IllegalStateException("secret-metadata-value");
                    }
                });

        assertSanitizedInvalid(() -> FunctionContext.of(
                RuleFacts.fromMap(Map.of()), Locale.ROOT, ZoneId.of("UTC"), leakingEntry),
                "secret-metadata-entry");
        assertSanitizedInvalid(() -> FunctionContext.of(
                RuleFacts.fromMap(Map.of()), Locale.ROOT, ZoneId.of("UTC"), leakingKey),
                "secret-metadata-key");
        assertSanitizedInvalid(() -> FunctionContext.of(
                RuleFacts.fromMap(Map.of()), Locale.ROOT, ZoneId.of("UTC"), leakingValue),
                "secret-metadata-value");
    }

    /**
     * 验证恶意列表和映射迭代异常在公开复制边界被净化。
     */
    @Test
    void shouldSanitizeHostileCollectionIterationFailures() {
        List<FactValue> hostileList = new AbstractList<>() {
            @Override public FactValue get(int index) { return FactValues.nullValue(); }
            @Override public int size() { return 1; }
            @Override
            public Iterator<FactValue> iterator() {
                throw new IllegalStateException("secret-arguments-list");
            }
        };
        Map<String, String> hostileMap = new AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return new java.util.AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, String>> iterator() {
                        throw new IllegalStateException("secret-context-map");
                    }
                    @Override public int size() { return 1; }
                };
            }
        };

        assertSanitizedInvalid(() -> FunctionArguments.of(hostileList), "secret-arguments-list");
        assertSanitizedInvalid(() -> FunctionContext.of(
                RuleFacts.fromMap(Map.of()), Locale.ROOT, ZoneId.of("UTC"), hostileMap),
                "secret-context-map");
    }

    /**
     * 验证注册表最多保存一千零二十四个函数。
     */
    @Test
    void shouldBoundRegistryAtOneThousandTwentyFourFunctions() {
        FunctionRegistry.Builder builder = FunctionRegistry.builder();
        for (int index = 0; index < 1024; index++) {
            builder.register(function("F" + index, "1", FunctionSignature.empty(), THREAD_SAFE));
        }

        assertThat(builder.build().requireDescriptor("F1023").code()).isEqualTo("F1023");
        assertInvalid(() -> builder.register(
                function("OVERFLOW", "1", FunctionSignature.empty(), THREAD_SAFE)));
    }

    /**
     * 验证所有公开构造入口拒绝空值、空白值和非法编码。
     */
    @Test
    void shouldRejectInvalidPublicInputsAndUnknownCodes() {
        assertInvalid(() -> FunctionCharacteristics.of(null, FunctionEffect.PURE,
                FunctionThreading.THREAD_SAFE));
        assertInvalid(() -> FunctionArguments.of((List<FactValue>) null));
        assertInvalid(() -> FunctionArguments.of(java.util.Arrays.asList(FactValues.nullValue(), null)));
        assertInvalid(() -> FunctionArguments.of(FactValues.nullValue()).get(-1));
        assertInvalid(() -> FunctionArguments.of(FactValues.nullValue()).get(1));
        assertInvalid(() -> FunctionContext.of(null, Locale.ROOT, ZoneId.of("UTC"), Map.of()));
        assertInvalid(() -> FunctionContext.of(RuleFacts.fromMap(Map.of()), null,
                ZoneId.of("UTC"), Map.of()));
        assertInvalid(() -> FunctionContext.of(RuleFacts.fromMap(Map.of()), Locale.ROOT,
                null, Map.of()));
        assertInvalid(() -> FunctionContext.of(RuleFacts.fromMap(Map.of()), Locale.ROOT,
                ZoneId.of("UTC"), Map.of(" ", "value")));
        assertInvalid(() -> FunctionContext.of(RuleFacts.fromMap(Map.of()), Locale.ROOT,
                ZoneId.of("UTC"), java.util.Collections.singletonMap("key", null)));
        assertInvalid(() -> descriptor(null, "1", oneStringArgument(), INTEGER, THREAD_SAFE));
        assertInvalid(() -> descriptor("bad-code", "1", oneStringArgument(), INTEGER, THREAD_SAFE));
        assertInvalid(() -> descriptor("LENGTH", " ", oneStringArgument(), INTEGER, THREAD_SAFE));
        assertInvalid(() -> descriptor("LENGTH", "1 bad", oneStringArgument(), INTEGER, THREAD_SAFE));
        assertInvalid(() -> descriptor("LENGTH", "1", null, INTEGER, THREAD_SAFE));
        assertInvalid(() -> descriptor("LENGTH", "1", oneStringArgument(), null, THREAD_SAFE));
        assertInvalid(() -> descriptor("LENGTH", "1", oneStringArgument(), INTEGER, null));

        FunctionRegistry registry = FunctionRegistry.builder().build();
        assertInvalid(() -> FunctionRegistry.builder().register((RuleFunction) null));
        assertInvalid(() -> FunctionRegistry.builder().register((RuleFunctionFactory) null));
        assertInvalid(() -> registry.requireDescriptor(null));
        assertInvalid(() -> registry.requireDescriptor(" "));
        assertInvalid(() -> registry.requireDescriptor("UNKNOWN"));
        assertInvalid(() -> registry.acquireForInvocation("UNKNOWN"));
    }

    /**
     * 构造单字符串参数签名。
     *
     * @return 单参数签名
     */
    private static FunctionSignature oneStringArgument() {
        return FunctionSignature.of(FunctionParameter.required("value", STRING));
    }

    /**
     * 构造函数特征。
     *
     * @param determinism 确定性
     * @param effect 副作用范围
     * @param threading 线程生命周期
     * @return 函数特征
     */
    private static FunctionCharacteristics characteristics(
            FunctionDeterminism determinism,
            FunctionEffect effect,
            FunctionThreading threading) {
        return FunctionCharacteristics.of(determinism, effect, threading);
    }

    /**
     * 构造测试函数，默认返回整数。
     *
     * @param code 编码
     * @param version 版本
     * @param signature 签名
     * @param characteristics 特征
     * @return 测试函数
     */
    private static RuleFunction function(
            String code,
            String version,
            FunctionSignature signature,
            FunctionCharacteristics characteristics) {
        return function(code, version, signature, INTEGER, characteristics);
    }

    /**
     * 构造具有指定返回类型的测试函数。
     *
     * @param code 编码
     * @param version 版本
     * @param signature 签名
     * @param returnType 返回类型
     * @param characteristics 特征
     * @return 测试函数
     */
    private static RuleFunction function(
            String code,
            String version,
            FunctionSignature signature,
            TypeDescriptor returnType,
            FunctionCharacteristics characteristics) {
        return new TestFunction(code, version, signature, returnType, characteristics);
    }

    /**
     * 构造不可变函数描述符。
     *
     * @param code 编码
     * @param version 版本
     * @param signature 签名
     * @param returnType 返回类型
     * @param characteristics 特征
     * @return 描述符
     */
    private static FunctionDescriptor descriptor(
            String code,
            String version,
            FunctionSignature signature,
            TypeDescriptor returnType,
            FunctionCharacteristics characteristics) {
        return FunctionDescriptor.of(code, version, signature, returnType, characteristics);
    }

    /**
     * 构造会统计实例创建次数的工厂。
     *
     * @param descriptor 描述符
     * @param creations 创建计数器
     * @return 测试工厂
     */
    private static RuleFunctionFactory factory(
            FunctionDescriptor descriptor,
            AtomicInteger creations) {
        return new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                creations.incrementAndGet();
                return function(
                        descriptor.code(), descriptor.semanticVersion(), descriptor.signature(),
                        descriptor.returnType(), descriptor.characteristics());
            }
        };
    }

    /**
     * 创建会等待另一个工厂同时进入的调用级工厂。
     *
     * @param code 函数编码
     * @param activeCreations 当前活跃创建数
     * @param maximumActiveCreations 最大活跃创建数
     * @param bothEntered 两个工厂均进入的闩锁
     * @return 调用级工厂
     */
    private static RuleFunctionFactory parallelFactory(
            String code,
            AtomicInteger activeCreations,
            AtomicInteger maximumActiveCreations,
            CountDownLatch bothEntered) {
        FunctionDescriptor descriptor = descriptor(
                code, "1", FunctionSignature.empty(), INTEGER, INVOCATION_SCOPED);
        AtomicInteger creations = new AtomicInteger();
        return new RuleFunctionFactory() {
            @Override public FunctionDescriptor descriptor() { return descriptor; }

            @Override
            public RuleFunction create() {
                if (creations.incrementAndGet() == 1) {
                    return function(code, "1", FunctionSignature.empty(), INVOCATION_SCOPED);
                }
                int active = activeCreations.incrementAndGet();
                maximumActiveCreations.accumulateAndGet(active, Math::max);
                bothEntered.countDown();
                try {
                    bothEntered.await(500, TimeUnit.MILLISECONDS);
                    return function(code, "1", FunctionSignature.empty(), INVOCATION_SCOPED);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    activeCreations.decrementAndGet();
                }
            }
        };
    }

    /**
     * 创建按指定次数返回元数据项的自定义映射。
     *
     * @param count 迭代项数量
     * @param factory 元数据项工厂
     * @return 自定义映射
     */
    private static Map<String, String> metadataMap(
            int count,
            java.util.function.IntFunction<Map.Entry<String, String>> factory) {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                return new java.util.AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, String>> iterator() {
                        return new Iterator<>() {
                            private int index;
                            @Override public boolean hasNext() { return index < count; }
                            @Override public Entry<String, String> next() {
                                return factory.apply(index++);
                            }
                        };
                    }
                    @Override public int size() { return count; }
                };
            }
        };
    }

    /**
     * 构造仅包含一个函数的注册表。
     *
     * @param function 函数
     * @return 注册表
     */
    private static FunctionRegistry registry(RuleFunction function) {
        return FunctionRegistry.builder().register(function).build();
    }

    /**
     * 构造工厂注册的注册表，用于比较线程特征摘要。
     *
     * @param code 编码
     * @param version 版本
     * @param signature 签名
     * @param returnType 返回类型
     * @param characteristics 调用级特征
     * @return 注册表
     */
    private static FunctionRegistry functionFactoryRegistryDescriptor(
            String code,
            String version,
            FunctionSignature signature,
            TypeDescriptor returnType,
            FunctionCharacteristics characteristics) {
        FunctionDescriptor descriptor = descriptor(
                code, version, signature, returnType, characteristics);
        return FunctionRegistry.builder()
                .register(factory(descriptor, new AtomicInteger()))
                .build();
    }

    /**
     * 断言函数元数据变化导致摘要变化。
     *
     * @param baseline 基准注册表
     * @param changed 变化后的函数
     */
    private static void assertDifferentDigest(
            FunctionRegistry baseline,
            RuleFunction changed) {
        assertThat(registry(changed).catalogDigest()).isNotEqualTo(baseline.catalogDigest());
    }

    /**
     * 断言函数元数据变化导致摘要变化。
     *
     * @param baseline 基准注册表
     * @param changed 变化后的注册表
     */
    private static void assertDifferentDigest(
            FunctionRegistry baseline,
            FunctionRegistry changed) {
        assertThat(changed.catalogDigest()).isNotEqualTo(baseline.catalogDigest());
    }

    /**
     * 断言操作抛出统一非法参数错误。
     *
     * @param operation 待执行操作
     */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 断言不可信异常被转换为不含原因和敏感文本的非法参数错误。
     *
     * @param operation 待执行操作
     * @param secret 不得出现在异常公开内容中的敏感文本
     */
    private static void assertSanitizedInvalid(Runnable operation, String secret) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain(secret);
                    assertThat(exception.getMessageArgs())
                            .allSatisfy(argument -> assertThat(String.valueOf(argument))
                                    .doesNotContain(secret));
                });
    }

    /**
     * 固定元数据的测试函数。
     */
    private static final class TestFunction implements RuleFunction {
        private final String code;
        private final String version;
        private final FunctionSignature signature;
        private final TypeDescriptor returnType;
        private final FunctionCharacteristics characteristics;

        /**
         * 创建测试函数。
         *
         * @param code 编码
         * @param version 版本
         * @param signature 签名
         * @param returnType 返回类型
         * @param characteristics 特征
         */
        private TestFunction(
                String code,
                String version,
                FunctionSignature signature,
                TypeDescriptor returnType,
                FunctionCharacteristics characteristics) {
            this.code = code;
            this.version = version;
            this.signature = signature;
            this.returnType = returnType;
            this.characteristics = characteristics;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String semanticVersion() {
            return version;
        }

        @Override
        public FunctionSignature signature() {
            return signature;
        }

        @Override
        public TypeDescriptor returnType() {
            return returnType;
        }

        @Override
        public FunctionCharacteristics characteristics() {
            return characteristics;
        }

        @Override
        public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            return FactValues.integer(1);
        }
    }
}
