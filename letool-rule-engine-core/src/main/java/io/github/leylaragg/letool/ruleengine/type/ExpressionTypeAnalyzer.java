package io.github.leylaragg.letool.ruleengine.type;

import io.github.leylaragg.letool.ruleengine.compile.ExpressionDependencies;
import io.github.leylaragg.letool.ruleengine.compile.ExpressionDependency;
import io.github.leylaragg.letool.ruleengine.compile.DependencyCoverage;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.expression.ast.AstNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BetweenNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.BinaryOperationNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.FunctionCallNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.ListLiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.LiteralNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.PathNode;
import io.github.leylaragg.letool.ruleengine.expression.ast.UnaryOperationNode;
import io.github.leylaragg.letool.ruleengine.expression.lexer.TokenType;
import io.github.leylaragg.letool.ruleengine.fact.FactPath;
import io.github.leylaragg.letool.ruleengine.fact.FactPathParser;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.function.FunctionFactAccess;
import io.github.leylaragg.letool.ruleengine.function.FunctionParameter;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 只读取 AST、事实契约和函数描述符的阶段一语义分析器。
 */
public final class ExpressionTypeAnalyzer {

    /** 即使调用方预算更大，单次分析也不保留超过此数的诊断。 */
    private static final int MAX_DIAGNOSTICS = 256;

    /** 逻辑和比较运算复用的非空布尔类型。 */
    private static final TypeDescriptor BOOLEAN = TypeDescriptor.scalar(TypeKind.BOOLEAN, false);

    /**
     * 分析 AST 的结果类型、事实依赖、函数依赖和语义诊断。
     *
     * @param root AST 根节点
     * @param factContract 事实契约
     * @param functionRegistry 只读函数目录
     * @param diagnosticLimit 本次最多保存的诊断数量
     * @return 不可变分析结果
     */
    public Analysis analyze(AstNode root, FactContract factContract,
            FunctionRegistry functionRegistry, int diagnosticLimit) {
        if (root == null || factContract == null || functionRegistry == null
                || diagnosticLimit <= 0) {
            throw RuleEngineException.invalidArgument();
        }
        Session session = new Session(factContract, functionRegistry,
                Math.min(MAX_DIAGNOSTICS, diagnosticLimit));
        return session.analyze(root);
    }

    /**
     * 不可变语义分析结果。
     */
    public static final class Analysis {
        /** 根表达式推导类型。 */
        private final TypeDescriptor resultType;

        /** 按首次出现顺序冻结的类型化事实依赖。 */
        private final ExpressionDependencies dependencies;

        /** 按首次出现顺序冻结的函数编码。 */
        private final List<String> functionDependencies;

        /** 当前静态事实依赖能否覆盖全部运行时事实读取。 */
        private final DependencyCoverage dependencyCoverage;

        /** 不可变语义诊断。 */
        private final List<RuleDiagnostic> diagnostics;

        /** 接收已经冻结的单次分析结果。 */
        private Analysis(
                TypeDescriptor resultType,
                ExpressionDependencies dependencies,
                List<String> functionDependencies,
                DependencyCoverage dependencyCoverage,
                List<RuleDiagnostic> diagnostics) {
            this.resultType = resultType;
            this.dependencies = dependencies;
            this.functionDependencies = List.copyOf(functionDependencies);
            this.dependencyCoverage = dependencyCoverage;
            this.diagnostics = List.copyOf(diagnostics);
        }

        /** @return 根表达式推导类型 */
        public TypeDescriptor resultType() { return resultType; }

        /** @return 类型化事实依赖 */
        public ExpressionDependencies dependencies() { return dependencies; }

        /** @return 按首次出现顺序排列的函数编码 */
        public List<String> functionDependencies() { return functionDependencies; }

        /** @return 静态依赖完整或包含动态事实访问 */
        public DependencyCoverage dependencyCoverage() { return dependencyCoverage; }

        /** @return 不可变语义诊断 */
        public List<RuleDiagnostic> diagnostics() { return diagnostics; }

        /** @return 无错误诊断时返回 {@code true} */
        public boolean isSuccessful() { return diagnostics.isEmpty(); }
    }

    /** 单次分析可变状态。 */
    private static final class Session {
        /** 路径类型查询使用的事实契约快照。 */
        private final FactContract contract;

        /** 函数签名查询使用的函数目录快照。 */
        private final FunctionRegistry registry;

        /** 调用方允许保留的诊断数量。 */
        private final int diagnosticLimit;

        /** 以节点身份保存的后序类型推导结果。 */
        private final IdentityHashMap<AstNode, TypeDescriptor> types = new IdentityHashMap<>();

        /** 按首次源码位置累积的事实依赖。 */
        private final List<ExpressionDependency> dependencies = new ArrayList<>();

        /** 按首次出现顺序去重的函数编码。 */
        private final Map<String, Integer> functions = new LinkedHashMap<>();

        /** 遇到动态事实函数后转为保守状态，后续分析不能恢复为完整。 */
        private DependencyCoverage dependencyCoverage = DependencyCoverage.COMPLETE;

        /** 在预算内累积的语义诊断。 */
        private final List<RuleDiagnostic> diagnostics = new ArrayList<>();

        /** 创建与其他编译隔离的分析会话。 */
        private Session(FactContract contract, FunctionRegistry registry, int diagnosticLimit) {
            this.contract = contract;
            this.registry = registry;
            this.diagnosticLimit = diagnosticLimit;
        }

        /** 以显式后序栈推导所有节点类型，避免 AST 深度消耗 JVM 栈。 */
        private Analysis analyze(AstNode root) {
            Deque<Entry> pending = new ArrayDeque<>();
            pending.push(new Entry(root, false));
            while (!pending.isEmpty() && diagnostics.size() < diagnosticLimit) {
                Entry entry = pending.pop();
                if (!entry.visited) {
                    pending.push(new Entry(entry.node, true));
                    List<AstNode> children = entry.node.children();
                    for (int index = children.size() - 1; index >= 0; index--) {
                        pending.push(new Entry(children.get(index), false));
                    }
                } else {
                    types.put(entry.node, infer(entry.node));
                }
            }
            TypeDescriptor result = types.getOrDefault(root, TypeCompatibility.unknown());
            List<String> orderedFunctions = functions.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            List<RuleDiagnostic> orderedDiagnostics = diagnostics.stream()
                    .sorted(Comparator.comparingInt(RuleDiagnostic::startPosition)
                            .thenComparingInt(RuleDiagnostic::endPosition)
                            .thenComparing(value -> value.code().code()))
                    .toList();
            return new Analysis(result, ExpressionDependencies.of(dependencies),
                    orderedFunctions, dependencyCoverage, orderedDiagnostics);
        }

        /** 按节点种类分派类型规则。 */
        private TypeDescriptor infer(AstNode node) {
            if (node instanceof LiteralNode literal) return literal(literal);
            if (node instanceof PathNode path) return path(path);
            if (node instanceof FunctionCallNode function) return function(function);
            if (node instanceof UnaryOperationNode unary) return unary(unary);
            if (node instanceof BinaryOperationNode binary) return binary(binary);
            if (node instanceof BetweenNode between) return between(between);
            return list((ListLiteralNode) node);
        }

        /** 将规范字面量 Token 映射为类型目录项。 */
        private TypeDescriptor literal(LiteralNode literal) {
            TypeKind kind = switch (literal.literalType()) {
                case STRING -> TypeKind.STRING;
                case BOOLEAN -> TypeKind.BOOLEAN;
                case INTEGER -> TypeKind.INTEGER;
                case DECIMAL -> TypeKind.DECIMAL;
                case NULL -> TypeKind.NULL;
                case DATE -> TypeKind.DATE;
                case DATETIME -> TypeKind.DATE_TIME;
                case INSTANT -> TypeKind.INSTANT;
                default -> throw RuleEngineException.invalidArgument();
            };
            if (kind == TypeKind.DATE || kind == TypeKind.DATE_TIME || kind == TypeKind.INSTANT) {
                try {
                    if (kind == TypeKind.DATE) {
                        LocalDate.parse(literal.normalizedValue(),
                                DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT));
                    } else if (kind == TypeKind.DATE_TIME) {
                        LocalDateTime.parse(literal.normalizedValue(),
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.STRICT));
                    } else {
                        Instant.parse(literal.normalizedValue());
                    }
                } catch (DateTimeException exception) {
                    diagnostic(RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL, literal, List.of());
                    return TypeCompatibility.unknown();
                }
            }
            return TypeDescriptor.scalar(kind, kind == TypeKind.NULL);
        }

        /** 查询路径契约并记录带首次源码范围的类型化依赖。 */
        private TypeDescriptor path(PathNode node) {
            FactPath path;
            try {
                path = FactPathParser.parse(node.normalizedPath());
            } catch (RuleEngineException exception) {
                diagnostic(RuleDiagnosticCode.INVALID_FACT_PATH, node, List.of());
                return TypeCompatibility.unknown();
            }
            Optional<TypeDescriptor> descriptor = contract.descriptor(path.toString());
            if (descriptor.isEmpty()) {
                diagnostic(RuleDiagnosticCode.UNKNOWN_FACT_PATH, node,
                        List.of(safeIdentifier(path.toString())));
                return TypeCompatibility.unknown();
            }
            dependencies.add(new ExpressionDependency(path, descriptor.get(),
                    node.startPosition(), node.endPosition()));
            return descriptor.get();
        }

        /** 校验函数存在性、参数签名并记录函数目录依赖。 */
        private TypeDescriptor function(FunctionCallNode node) {
            FunctionDescriptor descriptor;
            try {
                descriptor = registry.requireDescriptor(node.code());
            } catch (RuleEngineException exception) {
                diagnostic(RuleDiagnosticCode.UNKNOWN_FUNCTION, node,
                        List.of(safeIdentifier(node.code())));
                return TypeCompatibility.unknown();
            }
            functions.merge(descriptor.code(), node.startPosition(), Math::min);
            if (descriptor.factAccess() == FunctionFactAccess.DYNAMIC_FACTS) {
                dependencyCoverage = DependencyCoverage.DYNAMIC;
            }
            FunctionSignature signature = descriptor.signature();
            if (!signature.acceptsArgumentCount(node.arguments().size())) {
                diagnostic(RuleDiagnosticCode.ARGUMENT_COUNT_MISMATCH, node,
                        List.of(descriptor.code()));
                return descriptor.returnType();
            }
            List<FunctionParameter> parameters = signature.parameters();
            for (int index = 0; index < node.arguments().size(); index++) {
                FunctionParameter parameter = parameters.get(Math.min(index, parameters.size() - 1));
                TypeDescriptor actual = type(node.arguments().get(index));
                if (!TypeCompatibility.isAssignable(actual, parameter.type())) {
                    diagnostic(RuleDiagnosticCode.ARGUMENT_TYPE_MISMATCH,
                            node.arguments().get(index), List.of(descriptor.code()));
                }
            }
            return descriptor.returnType();
        }

        /** 按一元运算符约束操作数类型。 */
        private TypeDescriptor unary(UnaryOperationNode node) {
            TypeDescriptor operand = type(node.operand());
            return switch (node.operator()) {
                case IS_NULL, IS_NOT_NULL -> BOOLEAN;
                case NOT -> requireKind(node, operand, TypeKind.BOOLEAN) ? BOOLEAN
                        : TypeCompatibility.unknown();
                case PLUS, MINUS -> {
                    if (operand.kind() == TypeKind.UNKNOWN) yield TypeCompatibility.unknown();
                    if (!TypeCompatibility.isNumeric(operand)) {
                        mismatch(node);
                        yield TypeCompatibility.unknown();
                    }
                    yield operand;
                }
                default -> throw RuleEngineException.invalidArgument();
            };
        }

        /** 按逻辑、算术或比较类别推导二元结果。 */
        private TypeDescriptor binary(BinaryOperationNode node) {
            TypeDescriptor left = type(node.left());
            TypeDescriptor right = type(node.right());
            return switch (node.operator()) {
                case PLUS, MINUS, MULTIPLY, DIVIDE, MODULO -> {
                    TypeDescriptor result = TypeCompatibility.numericResult(left, right);
                    if (result.kind() == TypeKind.UNKNOWN
                            && left.kind() != TypeKind.UNKNOWN && right.kind() != TypeKind.UNKNOWN) {
                        mismatch(node);
                    }
                    yield result;
                }
                case AND, OR -> {
                    if (left.kind() == TypeKind.UNKNOWN || right.kind() == TypeKind.UNKNOWN) {
                        yield TypeCompatibility.unknown();
                    }
                    if (left.kind() != TypeKind.BOOLEAN || right.kind() != TypeKind.BOOLEAN) {
                        mismatch(node);
                        yield TypeCompatibility.unknown();
                    }
                    yield BOOLEAN;
                }
                case EQ, NE -> comparison(node,
                        TypeCompatibility.supportsEquality(left, right));
                case GT, GE, LT, LE -> comparison(node,
                        TypeCompatibility.supportsOrdering(left, right));
                case IN, NOT_IN -> in(node, left, right);
                default -> throw RuleEngineException.invalidArgument();
            };
        }

        /** 把比较兼容性转换为布尔结果或未知占位。 */
        private TypeDescriptor comparison(BinaryOperationNode node, boolean compatible) {
            if (!compatible) mismatch(node);
            return compatible ? BOOLEAN : TypeCompatibility.unknown();
        }

        /** 校验 IN 左值与列表元素的相等比较兼容性。 */
        private TypeDescriptor in(BinaryOperationNode node,
                TypeDescriptor left, TypeDescriptor right) {
            if (left.kind() == TypeKind.UNKNOWN || right.kind() == TypeKind.UNKNOWN) {
                return TypeCompatibility.unknown();
            }
            if (left.kind() == TypeKind.NULL || right.kind() != TypeKind.ARRAY
                    || right.elementType().kind() == TypeKind.NULL
                    || !TypeCompatibility.supportsEquality(left, right.elementType())) {
                mismatch(node);
                return TypeCompatibility.unknown();
            }
            return BOOLEAN;
        }

        /** 校验值与上下界属于同一排序域。 */
        private TypeDescriptor between(BetweenNode node) {
            TypeDescriptor value = type(node.value());
            TypeDescriptor lower = type(node.lowerBound());
            TypeDescriptor upper = type(node.upperBound());
            if (value.kind() == TypeKind.UNKNOWN || lower.kind() == TypeKind.UNKNOWN
                    || upper.kind() == TypeKind.UNKNOWN) {
                return TypeCompatibility.unknown();
            }
            if (!TypeCompatibility.supportsOrdering(value, lower)
                    || !TypeCompatibility.supportsOrdering(value, upper)) {
                mismatch(node);
                return TypeCompatibility.unknown();
            }
            return BOOLEAN;
        }

        /** 合并列表元素类型，保留数值提升和可空性。 */
        private TypeDescriptor list(ListLiteralNode node) {
            TypeDescriptor common = type(node.elements().get(0));
            for (int index = 1; index < node.elements().size(); index++) {
                TypeDescriptor current = type(node.elements().get(index));
                if (TypeCompatibility.isNumeric(common) && TypeCompatibility.isNumeric(current)) {
                    common = TypeCompatibility.numericResult(common, current);
                } else if (common.kind() != current.kind()) {
                    if (common.kind() != TypeKind.UNKNOWN && current.kind() != TypeKind.UNKNOWN) {
                        mismatch(node);
                    }
                    return TypeCompatibility.unknown();
                } else if (common.nullable() != current.nullable()) {
                    common = withNullable(common, true);
                }
            }
            return TypeDescriptor.array(common, false);
        }

        /** 要求节点属于指定类型，否则记录一次运算符类型诊断。 */
        private boolean requireKind(AstNode node, TypeDescriptor type, TypeKind expected) {
            if (type.kind() == TypeKind.UNKNOWN) return false;
            if (type.kind() == expected) return true;
            mismatch(node);
            return false;
        }

        /** 读取已完成后序推导的节点类型。 */
        private TypeDescriptor type(AstNode node) {
            return types.getOrDefault(node, TypeCompatibility.unknown());
        }

        /** 在不改变类型结构的前提下替换顶层可空性。 */
        private TypeDescriptor withNullable(TypeDescriptor type, boolean nullable) {
            return switch (type.kind()) {
                case ARRAY -> TypeDescriptor.array(type.elementType(), nullable);
                case OBJECT -> TypeDescriptor.object(nullable);
                default -> TypeDescriptor.scalar(type.kind(), nullable);
            };
        }

        /** 记录不带不安全值的运算符类型不匹配。 */
        private void mismatch(AstNode node) {
            diagnostic(RuleDiagnosticCode.OPERATOR_TYPE_MISMATCH, node, List.of());
        }

        /** 在诊断容量内保留首批语义问题。 */
        private void diagnostic(RuleDiagnosticCode code, AstNode node, List<Object> arguments) {
            if (diagnostics.size() < diagnosticLimit) {
                diagnostics.add(new RuleDiagnostic(code, DiagnosticSeverity.ERROR,
                        DiagnosticPhase.SEMANTIC, node.startPosition(), node.endPosition(),
                        arguments, null));
            }
        }

        /** 截断可进入公开诊断参数的函数或路径标识。 */
        private String safeIdentifier(String value) {
            int limit = RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH;
            return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
        }
    }

    /** 显式后序遍历栈项。 */
    private static final class Entry {
        /** 待处理 AST 节点。 */
        private final AstNode node;

        /** 是否已将子节点压栈，用于模拟后序遍历。 */
        private final boolean visited;

        /** 创建显式后序遍历栈项。 */
        private Entry(AstNode node, boolean visited) {
            this.node = node;
            this.visited = visited;
        }
    }
}
