package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建后只读、可并发查询的函数目录和实例提供器。
 */
public final class FunctionRegistry {

    /** 按规范函数编码索引的不可变注册快照。 */
    private final Map<String, Registration> registrations;

    /** 仅覆盖函数元数据、不依赖实例身份的目录指纹。 */
    private final String fingerprint;

    /** 从构建器当前注册项创建只读目录。 */
    private FunctionRegistry(Map<String, Registration> registrations) {
        this.registrations = Map.copyOf(registrations);
        this.fingerprint = calculateFingerprint(this.registrations);
    }

    /**
     * 创建函数注册表构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 只查询函数描述符，不创建函数实例。
     *
     * @param code 函数编码
     * @return 函数描述符
     */
    public FunctionDescriptor requireDescriptor(String code) {
        return requireRegistration(code).descriptor;
    }

    /**
     * 按线程模型获取本次调用使用的函数实例。
     *
     * @param code 函数编码
     * @return 共享实例或新调用级实例
     */
    public RuleFunction acquireForInvocation(String code) {
        Registration registration = requireRegistration(code);
        if (registration.sharedFunction != null) return registration.sharedFunction;
        // 工厂本身可能持有非线程安全的创建状态，注册表负责隔离同一工厂的并发访问。
        synchronized (registration) {
            RuleFunction function = createFromFactory(registration.factory);
            if (!registration.descriptor.equals(FunctionDescriptor.from(function))) {
                throw RuleEngineException.invalidArgument();
            }
            return function;
        }
    }

    /**
     * 覆盖排序后函数元数据的稳定目录指纹。
     *
     * @return 小写十六进制 SHA-256 指纹
     */
    public String fingerprint() {
        return fingerprint;
    }

    /** 规范化编码并要求目录中存在对应注册项。 */
    private Registration requireRegistration(String code) {
        String normalized = FunctionDescriptor.normalizeCode(code);
        Registration registration = registrations.get(normalized);
        if (registration == null) throw RuleEngineException.invalidArgument();
        return registration;
    }

    /** 在安全边界内调用宿主工厂并拒绝空实例。 */
    private static RuleFunction createFromFactory(RuleFunctionFactory factory) {
        try {
            RuleFunction function = factory.create();
            if (function == null) throw RuleEngineException.invalidArgument();
            return function;
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /** 按函数编码排序后计算与注册顺序无关的目录指纹。 */
    private static String calculateFingerprint(Map<String, Registration> registrations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, "LETOOL_FUNCTION_REGISTRY");
            add(digest, "1");
            List<FunctionDescriptor> descriptors = registrations.values().stream()
                    .map(registration -> registration.descriptor)
                    .sorted(java.util.Comparator.comparing(FunctionDescriptor::code))
                    .toList();
            add(digest, Integer.toString(descriptors.size()));
            for (FunctionDescriptor descriptor : descriptors) {
                addDescriptor(digest, descriptor);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** 按稳定字段顺序写入一条完整函数描述。 */
    private static void addDescriptor(MessageDigest digest, FunctionDescriptor descriptor) {
        add(digest, descriptor.code());
        add(digest, descriptor.semanticVersion());
        add(digest, Integer.toString(descriptor.signature().parameters().size()));
        for (FunctionParameter parameter : descriptor.signature().parameters()) {
            add(digest, parameter.name());
            add(digest, parameter.type().toCanonicalString());
            add(digest, Boolean.toString(parameter.optional()));
            add(digest, Boolean.toString(parameter.varargs()));
        }
        add(digest, descriptor.returnType().toCanonicalString());
        add(digest, descriptor.characteristics().determinism().name());
        add(digest, descriptor.characteristics().effect().name());
        add(digest, descriptor.characteristics().threading().name());
    }

    /** 以 UTF-8 长度前缀写入字段，避免拼接边界歧义。 */
    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    /**
     * 仅在构建阶段可变的函数注册器。
     *
     * <p>构建器不是线程安全类型，只能由一个配置线程使用；
     * {@link #build()} 产生的注册表是只读且线程安全的。</p>
     */
    public static final class Builder {
        /** 单个目录允许注册的函数数量上限。 */
        private static final int MAX_FUNCTIONS = 1024;

        /** 构建期间按注册顺序保存的函数项。 */
        private final Map<String, Registration> registrations = new LinkedHashMap<>();

        /** 创建单线程使用的空目录构建器。 */
        private Builder() { }

        /**
         * 注册线程安全共享函数实例。
         *
         * @param function 线程安全函数
         * @return 当前构建器
         */
        public Builder register(RuleFunction function) {
            FunctionDescriptor descriptor = FunctionDescriptor.from(function);
            if (descriptor.characteristics().threading() != FunctionThreading.THREAD_SAFE) {
                throw RuleEngineException.invalidArgument();
            }
            put(new Registration(descriptor, function, null));
            return this;
        }

        /**
         * 注册调用级隔离函数工厂并执行一次元数据探测。
         *
         * @param factory 调用级函数工厂
         * @return 当前构建器
         */
        public Builder register(RuleFunctionFactory factory) {
            if (factory == null) throw RuleEngineException.invalidArgument();
            FunctionDescriptor descriptor;
            try {
                descriptor = factory.descriptor();
            } catch (RuntimeException exception) {
                throw RuleEngineException.invalidArgument();
            }
            if (descriptor == null
                    || descriptor.characteristics().threading()
                    != FunctionThreading.INVOCATION_SCOPED) {
                throw RuleEngineException.invalidArgument();
            }
            ensureAbsent(descriptor.code());
            RuleFunction probe = createFromFactory(factory);
            if (!descriptor.equals(FunctionDescriptor.from(probe))) {
                throw RuleEngineException.invalidArgument();
            }
            put(new Registration(descriptor, null, factory));
            return this;
        }

        /**
         * 构建只读注册表快照。
         *
         * @return 只读注册表
         */
        public FunctionRegistry build() {
            return new FunctionRegistry(registrations);
        }

        /** 在容量和冲突检查后保存注册项。 */
        private void put(Registration registration) {
            ensureAbsent(registration.descriptor.code());
            registrations.put(registration.descriptor.code(), registration);
        }

        /** 同时执行函数编码冲突和目录容量检查。 */
        private void ensureAbsent(String code) {
            if (registrations.containsKey(code)) {
                throw RuleEngineException.registrationConflict();
            }
            if (registrations.size() >= MAX_FUNCTIONS) {
                throw RuleEngineException.invalidArgument();
            }
        }
    }

    /** 固化描述，并按线程模型保存共享实例或调用级工厂。 */
    private static final class Registration {
        /** 注册时冻结的函数元数据。 */
        private final FunctionDescriptor descriptor;

        /** 线程安全模型使用的共享实例，否则为 {@code null}。 */
        private final RuleFunction sharedFunction;

        /** 调用级模型使用的工厂，否则为 {@code null}。 */
        private final RuleFunctionFactory factory;

        /** 创建已经完成线程模型校验的注册项。 */
        private Registration(
                FunctionDescriptor descriptor,
                RuleFunction sharedFunction,
                RuleFunctionFactory factory) {
            this.descriptor = descriptor;
            this.sharedFunction = sharedFunction;
            this.factory = factory;
        }
    }
}
