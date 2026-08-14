package com.github.leyland.letool.ruleengine.api;

import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.compile.ExpressionCompiler;
import com.github.leyland.letool.ruleengine.evaluate.DefaultExpressionEvaluator;
import com.github.leyland.letool.ruleengine.evaluate.ExpressionEvaluator;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.function.FunctionRegistry;
import com.github.leyland.letool.ruleengine.function.FunctionDescriptor;
import com.github.leyland.letool.ruleengine.function.RuleFunction;
import com.github.leyland.letool.ruleengine.function.RuleFunctionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式引擎的可变配置构建器。
 *
 * <p>构建器不是线程安全类型，只能由一个配置线程使用。每次 {@link #build()}
 * 都生成与后续构建器修改隔离的引擎快照。</p>
 */
public final class ExpressionEngineBuilder {

    /** 待固化的编译器，默认使用 core 实现。 */
    private ExpressionCompiler compiler = new DefaultExpressionCompiler();

    /** 待固化的求值器，默认使用 core 实现。 */
    private ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();

    /** 待固化的资源上限。 */
    private EngineLimits limits = EngineLimits.defaults();

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
     * 注入宿主编译器 SPI。
     *
     * @param compiler 非空编译器
     * @return 当前构建器
     */
    public ExpressionEngineBuilder compiler(ExpressionCompiler compiler) {
        if (compiler == null) throw RuleEngineException.invalidArgument();
        this.compiler = compiler;
        return this;
    }

    /**
     * 注入宿主求值器 SPI；门面治理校验仍会先于该 SPI 执行。
     *
     * @param evaluator 非空求值器
     * @return 当前构建器
     */
    public ExpressionEngineBuilder evaluator(ExpressionEvaluator evaluator) {
        if (evaluator == null) throw RuleEngineException.invalidArgument();
        this.evaluator = evaluator;
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
        return new DefaultExpressionEngine(compiler, evaluator, limits, registry.build());
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
                    != com.github.leyland.letool.ruleengine.function.FunctionThreading.THREAD_SAFE) {
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
        @Override public com.github.leyland.letool.ruleengine.function.FunctionSignature signature() {
            return descriptor.signature();
        }
        /** {@inheritDoc} */
        @Override public com.github.leyland.letool.ruleengine.type.TypeDescriptor returnType() {
            return descriptor.returnType();
        }
        /** {@inheritDoc} */
        @Override public com.github.leyland.letool.ruleengine.function.FunctionCharacteristics characteristics() {
            return descriptor.characteristics();
        }
        /** {@inheritDoc} */
        @Override
        public com.github.leyland.letool.ruleengine.fact.FactValue execute(
                com.github.leyland.letool.ruleengine.function.FunctionArguments arguments,
                com.github.leyland.letool.ruleengine.function.FunctionContext context) {
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
