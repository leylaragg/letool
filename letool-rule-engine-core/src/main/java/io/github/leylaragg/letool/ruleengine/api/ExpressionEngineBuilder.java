package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import io.github.leylaragg.letool.ruleengine.evaluate.DefaultValueSummarizer;
import io.github.leylaragg.letool.ruleengine.evaluate.ValueSummarizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式引擎的可变配置构建器。
 *
 * <p>构建器不是线程安全类型，只能由一个配置线程使用。每次 {@link #build()}
 * 都生成与后续构建器修改隔离的引擎快照。</p>
 */
public final class ExpressionEngineBuilder {

    /** 待固化的资源上限。 */
    private EngineLimits limits = EngineLimits.defaults();

    /** 只影响轨迹展示、不参与表达式求值语义的值摘要器。 */
    private ValueSummarizer valueSummarizer = new DefaultValueSummarizer();

    /** 按注册顺序保存的函数及工厂元数据快照。 */
    private final List<Registration> registrations = new ArrayList<>();

    /** 包内创建构建器，调用方通过 {@link ExpressionEngine#builder()} 获取。 */
    ExpressionEngineBuilder() {
    }

    /**
     * 注册可在线程间共享的函数实例。
     *
     * @param function 线程安全函数实例
     * @return 当前构建器
     */
    public ExpressionEngineBuilder registerFunction(RuleFunction function) {
        if (function == null) throw RuleEngineException.invalidArgument();
        FunctionRegistration registration = new FunctionRegistration(function);
        ensureNoDuplicate(registration.code());
        registrations.add(registration);
        return this;
    }

    /**
     * 注册调用级函数工厂。
     *
     * @param factory 调用级函数工厂
     * @return 当前构建器
     */
    public ExpressionEngineBuilder registerFunction(RuleFunctionFactory factory) {
        if (factory == null) throw RuleEngineException.invalidArgument();
        FactoryRegistration registration = new FactoryRegistration(factory);
        ensureNoDuplicate(registration.code());
        registrations.add(registration);
        return this;
    }

    /**
     * 配置引擎编译和默认求值使用的资源限制。
     *
     * @param limits 非空资源限制
     * @return 当前构建器
     */
    public ExpressionEngineBuilder limits(EngineLimits limits) {
        if (limits == null) throw RuleEngineException.invalidArgument();
        this.limits = limits;
        return this;
    }

    /**
     * 配置轨迹节点使用的有界值摘要器。
     *
     * <p>摘要器只负责展示，不能改变事实、类型或求值结果；框架仍会净化控制字符并
     * 按资源限制截断输出。</p>
     *
     * @param valueSummarizer 宿主提供的安全摘要策略
     * @return 当前构建器
     */
    public ExpressionEngineBuilder valueSummarizer(ValueSummarizer valueSummarizer) {
        if (valueSummarizer == null) throw RuleEngineException.invalidArgument();
        this.valueSummarizer = valueSummarizer;
        return this;
    }

    /**
     * 构建不可变引擎快照。
     *
     * @return 与后续配置修改隔离的引擎
     */
    public ExpressionEngine build() {
        FunctionRegistry.Builder registry = FunctionRegistry.builder();
        for (Registration registration : List.copyOf(registrations)) registration.register(registry);
        return new DefaultExpressionEngine(limits, registry.build(), valueSummarizer);
    }

    /** 在构建前拒绝函数编码冲突，避免依赖注册顺序决定覆盖关系。 */
    private void ensureNoDuplicate(String code) {
        for (Registration registration : registrations) {
            if (registration.code().equals(code)) throw RuleEngineException.registrationConflict();
        }
    }

    /** 将共享实例与调用级工厂统一为可冻结的注册项。 */
    private sealed interface Registration permits FunctionRegistration, FactoryRegistration {
        /** @return 已冻结的函数编码 */
        String code();

        /** 把注册项写入本次构建使用的目录。 */
        void register(FunctionRegistry.Builder registry);
    }

    /** 共享函数实例的注册项。 */
    private static final class FunctionRegistration implements Registration {
        /** 已包装为固定元数据的函数实例。 */
        private final RuleFunction function;

        /** 用于冲突检查的稳定函数编码。 */
        private final String code;

        /** 在注册时读取并冻结宿主函数元数据。 */
        private FunctionRegistration(RuleFunction function) {
            FunctionDescriptor descriptor = FunctionDescriptor.from(function);
            this.function = new FixedDescriptorFunction(descriptor, function);
            this.code = descriptor.code();
        }

        /** {@inheritDoc} */
        @Override public String code() { return code; }

        /** {@inheritDoc} */
        @Override public void register(FunctionRegistry.Builder registry) { registry.register(function); }
    }

    /**
     * 固化宿主函数元数据，仅把执行调用委托给原实例。
     */
    private static final class FixedDescriptorFunction implements RuleFunction {
        /** 注册时冻结的函数元数据。 */
        private final FunctionDescriptor descriptor;

        /** 仅负责执行的宿主函数。 */
        private final RuleFunction delegate;

        /** 创建元数据固定、执行行为委托的共享函数。 */
        private FixedDescriptorFunction(FunctionDescriptor descriptor, RuleFunction delegate) {
            if (descriptor.characteristics().threading()
                    != io.github.leylaragg.letool.ruleengine.function.FunctionThreading.THREAD_SAFE) {
                throw RuleEngineException.invalidArgument();
            }
            this.descriptor = descriptor;
            this.delegate = delegate;
        }

        /** {@inheritDoc} */
        @Override public String code() { return descriptor.code(); }

        /** {@inheritDoc} */
        @Override public String semanticVersion() { return descriptor.semanticVersion(); }

        /** {@inheritDoc} */
        @Override public io.github.leylaragg.letool.ruleengine.function.FunctionSignature signature() {
            return descriptor.signature();
        }
        /** {@inheritDoc} */
        @Override public io.github.leylaragg.letool.ruleengine.type.TypeDescriptor returnType() {
            return descriptor.returnType();
        }
        /** {@inheritDoc} */
        @Override public io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics characteristics() {
            return descriptor.characteristics();
        }
        /** {@inheritDoc} */
        @Override
        public io.github.leylaragg.letool.ruleengine.function.FunctionFactAccess factAccess() {
            return descriptor.factAccess();
        }
        /** {@inheritDoc} */
        @Override
        public io.github.leylaragg.letool.ruleengine.fact.FactValue execute(
                io.github.leylaragg.letool.ruleengine.function.FunctionArguments arguments,
                io.github.leylaragg.letool.ruleengine.function.FunctionContext context) {
            return delegate.execute(arguments, context);
        }
    }

    /** 调用级函数工厂的注册项。 */
    private static final class FactoryRegistration implements Registration {
        /** 实际创建调用级函数的宿主工厂。 */
        private final RuleFunctionFactory factory;

        /** 用于冲突检查的稳定函数编码。 */
        private final String code;

        /** 注册时冻结的工厂元数据。 */
        private final FunctionDescriptor descriptor;

        /** 在注册时读取并冻结宿主工厂元数据。 */
        private FactoryRegistration(RuleFunctionFactory factory) {
            this.factory = factory;
            this.descriptor = descriptor(factory);
            this.code = descriptor.code();
        }

        /** 在安全边界内读取工厂描述，屏蔽宿主异常文本。 */
        private static FunctionDescriptor descriptor(RuleFunctionFactory factory) {
            try {
                FunctionDescriptor descriptor = factory.descriptor();
                if (descriptor == null) throw RuleEngineException.invalidArgument();
                return descriptor;
            } catch (RuleEngineException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw RuleEngineException.invalidArgument();
            }
        }

        /** {@inheritDoc} */
        @Override public String code() { return code; }

        /** 注册保留冻结描述、每次调用仍委托原工厂创建实例的包装工厂。 */
        @Override public void register(FunctionRegistry.Builder registry) {
            registry.register(new RuleFunctionFactory() {
                /** 返回注册时冻结的描述，避免宿主后续改变编译语义。 */
                @Override
                public FunctionDescriptor descriptor() {
                    return descriptor;
                }

                /** 每次求值调用仍由宿主工厂创建独立函数实例。 */
                @Override
                public RuleFunction create() {
                    return factory.create();
                }
            });
        }
    }
}
