package com.github.leyland.letool.ruleengine.evaluate;

import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticPhase;
import com.github.leyland.letool.ruleengine.diagnostic.DiagnosticSeverity;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnostic;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.expression.ast.*;
import com.github.leyland.letool.ruleengine.expression.lexer.TokenType;
import com.github.leyland.letool.ruleengine.fact.*;
import com.github.leyland.letool.ruleengine.function.*;
import com.github.leyland.letool.ruleengine.type.TypeCompatibility;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 基于已编译 AST 执行标量语义、短路函数调用和安全轨迹的默认求值器。
 */
public final class DefaultExpressionEvaluator implements ExpressionEvaluator {

    /** 小数运算统一使用 IEEE 754 decimal128 精度和舍入规则。 */
    private static final MathContext DECIMAL_CONTEXT = MathContext.DECIMAL128;

    /** 仅用于受限轨迹展示，不参与求值语义。 */
    private final ValueSummarizer summarizer;

    /** 创建使用默认安全摘要器的无状态求值器。 */
    public DefaultExpressionEvaluator() {
        this(new DefaultValueSummarizer());
    }

    /**
     * 创建使用宿主脱敏摘要器的无状态求值器。
     *
     * @param summarizer 值摘要器；其输出仍受框架长度边界约束
     */
    public DefaultExpressionEvaluator(ValueSummarizer summarizer) {
        if (summarizer == null) throw RuleEngineException.invalidArgument();
        this.summarizer = summarizer;
    }

    /** {@inheritDoc} */
    @Override
    public ExpressionEvaluationResult evaluate(CompiledExpression expression, RuleFacts facts,
            FunctionRegistry functionRegistry, EvaluationOptions options) {
        if (expression == null || facts == null || functionRegistry == null || options == null) {
            throw RuleEngineException.invalidArgument();
        }
        EvaluationSession session = new EvaluationSession(
                expression.fingerprint(), facts, options, summarizer);
        ExpressionEvaluationResult dependencyFailure = RuntimeFactValidator.validate(expression, facts);
        if (dependencyFailure != null) return dependencyFailure;
        try {
            RuntimeValue evaluated = evaluateNode(expression.ast(), facts, functionRegistry, session);
            if (evaluated.list != null || !FactValueTypes.isAssignable(
                    evaluated.value, expression.resultType())) {
                throw new EvaluationFailure(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH,
                        expression.ast(), List.of(), RuleEngineException.evaluationFailed(
                        new IllegalStateException("compiled result type invariant")));
            }
            return ExpressionEvaluationResult.success(evaluated.value, session.trace());
        } catch (EvaluationSession.FunctionLimitFailure failure) {
            session.traceFailure(failure.node());
            return failure(failure.node(), RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED,
                    List.of(failure.functionCode()), failure.failure(), session);
        } catch (EvaluationFailure failure) {
            session.traceFailure(failure.node);
            return failure(failure.node, failure.code, failure.arguments,
                    failure.frameworkException, session);
        } catch (ArithmeticException exception) {
            session.traceFailure(expression.ast());
            return failure(expression.ast(), RuleDiagnosticCode.EVALUATION_ERROR,
                    List.of(), RuleEngineException.evaluationFailed(exception), session);
        }
    }

    /**
     * 用显式帧栈执行 AST；AND、OR 在压入右子树前完成短路判断。
     */
    private RuntimeValue evaluateNode(AstNode node, RuleFacts facts,
            FunctionRegistry registry, EvaluationSession session) {
        Deque<EvaluationFrame> frames = new ArrayDeque<>();
        frames.addLast(new EvaluationFrame(node));
        RuntimeValue last = null;
        while (!frames.isEmpty()) {
            EvaluationFrame frame = frames.peekLast();
            AstNode current = frame.node;
            RuntimeValue completed = null;
            if (current instanceof LiteralNode literal) {
                completed = RuntimeValue.value(literal(literal));
            } else if (current instanceof PathNode path) {
                FactValue value = facts.resolve(path.normalizedPath()).orElseThrow(
                        () -> new EvaluationFailure(RuleDiagnosticCode.MISSING_FACT_VALUE,
                                current, List.of(bounded(path.normalizedPath())),
                                RuleEngineException.evaluationFailed(
                                        new IllegalStateException("missing fact"))));
                completed = RuntimeValue.value(value);
            } else if (current instanceof UnaryOperationNode unary) {
                if (frame.stage++ == 0) {
                    frames.addLast(new EvaluationFrame(unary.operand()));
                    continue;
                }
                completed = unary(unary, last);
            } else if (current instanceof BinaryOperationNode binary) {
                if (frame.stage == 0) {
                    frame.stage = 1;
                    frames.addLast(new EvaluationFrame(binary.left()));
                    continue;
                }
                if (frame.stage == 1) {
                    frame.values.add(last);
                    FactValue left = requireScalar(last, binary.left());
                    if (binary.operator() == TokenType.AND && !booleanValue(left, binary.left())) {
                        completed = RuntimeValue.value(FactValues.booleanValue(false));
                    } else if (binary.operator() == TokenType.OR && booleanValue(left, binary.left())) {
                        completed = RuntimeValue.value(FactValues.booleanValue(true));
                    } else {
                        frame.stage = 2;
                        frames.addLast(new EvaluationFrame(binary.right()));
                        continue;
                    }
                } else {
                    completed = binary(binary, frame.values.get(0), last);
                }
            } else if (current instanceof BetweenNode between) {
                if (frame.stage < 3) {
                    if (frame.stage > 0) frame.values.add(last);
                    AstNode child = between.children().get(frame.stage++);
                    frames.addLast(new EvaluationFrame(child));
                    continue;
                }
                frame.values.add(last);
                completed = between(between, frame.values);
            } else if (current instanceof ListLiteralNode list) {
                if (frame.stage < list.elements().size()) {
                    if (frame.stage > 0) frame.values.add(last);
                    frames.addLast(new EvaluationFrame(list.elements().get(frame.stage++)));
                    continue;
                }
                frame.values.add(last);
                List<FactValue> values = new ArrayList<>(frame.values.size());
                for (int index = 0; index < frame.values.size(); index++) {
                    values.add(requireScalar(frame.values.get(index), list.elements().get(index)));
                }
                completed = RuntimeValue.list(List.copyOf(values));
            } else if (current instanceof FunctionCallNode function) {
                if (frame.stage < function.arguments().size()) {
                    if (frame.stage > 0) frame.values.add(last);
                    frames.addLast(new EvaluationFrame(function.arguments().get(frame.stage++)));
                    continue;
                }
                if (!function.arguments().isEmpty()) frame.values.add(last);
                completed = function(function, frame.values, registry, session);
            } else {
                throw RuleEngineException.invalidArgument();
            }
            frames.removeLast();
            last = completed;
            if (completed.value != null) {
                session.traceValue(current, completed.value, FactValueTypes.typeOf(completed.value));
            }
        }
        return last;
    }

    /** 按目录线程模型取得函数，并校验运行时返回类型。 */
    private RuntimeValue function(FunctionCallNode node, List<RuntimeValue> runtimeArguments,
            FunctionRegistry registry, EvaluationSession session) {
        List<FactValue> arguments = new ArrayList<>(runtimeArguments.size());
        for (int index = 0; index < runtimeArguments.size(); index++) {
            arguments.add(requireScalar(runtimeArguments.get(index), node.arguments().get(index)));
        }
        int invocationIndex = session.nextFunctionInvocation(node.code(), node);
        FunctionDescriptor descriptor = registry.requireDescriptor(node.code());
        FactValue value;
        try {
            RuleFunction function = registry.acquireForInvocation(node.code());
            value = function.execute(FunctionArguments.of(arguments),
                    session.functionContext(node.code(), invocationIndex));
        } catch (RuntimeException exception) {
            throw new EvaluationFailure(RuleDiagnosticCode.FUNCTION_EXECUTION_ERROR,
                    node, List.of(node.code()), RuleEngineException.evaluationFailed(exception));
        }
        if (value == null || !FactValueTypes.isAssignable(value, descriptor.returnType())) {
            throw new EvaluationFailure(RuleDiagnosticCode.RUNTIME_TYPE_MISMATCH,
                    node, List.of(node.code()), RuleEngineException.evaluationFailed(
                    new IllegalStateException("function return type mismatch")));
        }
        return RuntimeValue.value(value);
    }

    /** 执行二元标量或集合成员关系语义。 */
    private RuntimeValue binary(
            BinaryOperationNode node, RuntimeValue leftRuntime, RuntimeValue rightRuntime) {
        FactValue left = requireScalar(leftRuntime, node.left());
        if (node.operator() == TokenType.IN || node.operator() == TokenType.NOT_IN) {
            if (rightRuntime.list == null) throw invariant(node);
            boolean contains = rightRuntime.list.stream().anyMatch(value -> equal(left, value));
            return RuntimeValue.value(FactValues.booleanValue(
                    node.operator() == TokenType.IN ? contains : !contains));
        }
        FactValue right = requireScalar(rightRuntime, node.right());
        return RuntimeValue.value(switch (node.operator()) {
            case AND -> FactValues.booleanValue(booleanValue(left, node.left())
                    && booleanValue(right, node.right()));
            case OR -> FactValues.booleanValue(booleanValue(left, node.left())
                    || booleanValue(right, node.right()));
            case PLUS, MINUS, MULTIPLY, DIVIDE, MODULO -> arithmetic(node, left, right);
            case EQ -> FactValues.booleanValue(equal(left, right));
            case NE -> FactValues.booleanValue(!equal(left, right));
            case GT -> FactValues.booleanValue(compare(left, right, node) > 0);
            case GE -> FactValues.booleanValue(compare(left, right, node) >= 0);
            case LT -> FactValues.booleanValue(compare(left, right, node) < 0);
            case LE -> FactValues.booleanValue(compare(left, right, node) <= 0);
            default -> throw invariant(node);
        });
    }

    /** 使用包含上下界的同域比较执行 BETWEEN。 */
    private RuntimeValue between(BetweenNode node, List<RuntimeValue> values) {
        FactValue value = requireScalar(values.get(0), node.value());
        FactValue lower = requireScalar(values.get(1), node.lowerBound());
        FactValue upper = requireScalar(values.get(2), node.upperBound());
        return RuntimeValue.value(FactValues.booleanValue(
                compare(value, lower, node) >= 0 && compare(value, upper, node) <= 0));
    }

    /** 执行逻辑非、数值正负号或空值判断。 */
    private static RuntimeValue unary(UnaryOperationNode node, RuntimeValue runtime) {
        FactValue value = requireScalar(runtime, node.operand());
        return RuntimeValue.value(switch (node.operator()) {
            case IS_NULL -> FactValues.booleanValue(value.kind() == FactKind.NULL);
            case IS_NOT_NULL -> FactValues.booleanValue(value.kind() != FactKind.NULL);
            case NOT -> FactValues.booleanValue(!booleanValue(value, node));
            case PLUS -> requireNumeric(value, node);
            case MINUS -> negate(value, node);
            default -> throw invariant(node);
        });
    }

    /** 从 Lexer 规范文本构造对应不可变事实值。 */
    private static FactValue literal(LiteralNode node) {
        String value = node.normalizedValue();
        return switch (node.literalType()) {
            case STRING -> FactValues.string(value);
            case BOOLEAN -> FactValues.booleanValue(Boolean.parseBoolean(value));
            case INTEGER -> FactValues.integer(new BigInteger(value));
            case DECIMAL -> FactValues.decimal(new BigDecimal(value));
            case NULL -> FactValues.nullValue();
            case DATE -> FactValues.date(LocalDate.parse(value));
            case DATETIME -> FactValues.dateTime(LocalDateTime.parse(value));
            case INSTANT -> FactValues.instant(Instant.parse(value));
            default -> throw invariant(node);
        };
    }

    /** 使用整数精确运算或 DECIMAL128 小数运算执行算术表达式。 */
    private static FactValue arithmetic(BinaryOperationNode node, FactValue left, FactValue right) {
        requireNumeric(left, node);
        requireNumeric(right, node);
        boolean decimal = left.kind() == FactKind.DECIMAL || right.kind() == FactKind.DECIMAL;
        if (!decimal) {
            BigInteger a = (BigInteger) left.toSafeJavaValue();
            BigInteger b = (BigInteger) right.toSafeJavaValue();
            return switch (node.operator()) {
                case PLUS -> FactValues.integer(a.add(b));
                case MINUS -> FactValues.integer(a.subtract(b));
                case MULTIPLY -> FactValues.integer(a.multiply(b));
                case DIVIDE -> FactValues.integer(a.divide(b));
                case MODULO -> FactValues.integer(a.remainder(b));
                default -> throw invariant(node);
            };
        }
        BigDecimal a = decimal(left);
        BigDecimal b = decimal(right);
        return switch (node.operator()) {
            case PLUS -> FactValues.decimal(a.add(b));
            case MINUS -> FactValues.decimal(a.subtract(b));
            case MULTIPLY -> FactValues.decimal(a.multiply(b));
            case DIVIDE -> FactValues.decimal(a.divide(b, DECIMAL_CONTEXT));
            case MODULO -> FactValues.decimal(a.remainder(b, DECIMAL_CONTEXT));
            default -> throw invariant(node);
        };
    }

    /** 对整数或小数取相反数，不做隐式类型转换。 */
    private static FactValue negate(FactValue value, AstNode node) {
        requireNumeric(value, node);
        return value.kind() == FactKind.INTEGER
                ? FactValues.integer(((BigInteger) value.toSafeJavaValue()).negate())
                : FactValues.decimal(((BigDecimal) value.toSafeJavaValue()).negate());
    }

    /** 保护已编译 AST 的数值类型不变量。 */
    private static FactValue requireNumeric(FactValue value, AstNode node) {
        if (value.kind() != FactKind.INTEGER && value.kind() != FactKind.DECIMAL) throw invariant(node);
        return value;
    }

    /** 按空值、数值提升或同类标量规则判断相等。 */
    private static boolean equal(FactValue left, FactValue right) {
        if (left.kind() == FactKind.NULL || right.kind() == FactKind.NULL) {
            return left.kind() == right.kind();
        }
        if (numeric(left) && numeric(right)) return decimal(left).compareTo(decimal(right)) == 0;
        return left.kind() == right.kind() && left.equals(right);
    }

    /** 仅比较编译期允许的数值或同类可排序标量。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(FactValue left, FactValue right, AstNode node) {
        if (left.kind() == FactKind.NULL || right.kind() == FactKind.NULL) {
            throw new EvaluationFailure(RuleDiagnosticCode.EVALUATION_ERROR, node, List.of(),
                    RuleEngineException.evaluationFailed(new IllegalStateException("null ordering")));
        }
        if (numeric(left) && numeric(right)) return decimal(left).compareTo(decimal(right));
        if (left.kind() != right.kind() || switch (left.kind()) {
            case STRING, DATE, DATE_TIME, INSTANT -> false;
            default -> true;
        }) throw invariant(node);
        return ((Comparable) left.toSafeJavaValue()).compareTo(right.toSafeJavaValue());
    }

    /** 判断运行时值是否处于统一数值域。 */
    private static boolean numeric(FactValue value) {
        return value.kind() == FactKind.INTEGER || value.kind() == FactKind.DECIMAL;
    }

    /** 将整数精确提升为小数，已有小数保持原值。 */
    private static BigDecimal decimal(FactValue value) {
        return value.kind() == FactKind.INTEGER
                ? new BigDecimal((BigInteger) value.toSafeJavaValue())
                : (BigDecimal) value.toSafeJavaValue();
    }

    /** 保护已编译 AST 的布尔类型不变量并解包值。 */
    private static boolean booleanValue(FactValue value, AstNode node) {
        if (value.kind() != FactKind.BOOLEAN) throw invariant(node);
        return (Boolean) value.toSafeJavaValue();
    }

    /** 拒绝把 IN 列表中间值误用于标量位置。 */
    private static FactValue requireScalar(RuntimeValue runtime, AstNode node) {
        if (runtime.value == null) throw invariant(node);
        return runtime.value;
    }

    /** 将不可能的已编译 AST 状态转为固定安全失败。 */
    private static EvaluationFailure invariant(AstNode node) {
        return new EvaluationFailure(RuleDiagnosticCode.EVALUATION_ERROR, node, List.of(),
                RuleEngineException.evaluationFailed(new IllegalStateException("compiled AST invariant")));
    }

    /** 构建不回显底层异常文本的结构化求值失败结果。 */
    private static ExpressionEvaluationResult failure(AstNode node, RuleDiagnosticCode code,
            List<Object> arguments, RuleEngineException exception, EvaluationSession session) {
        return ExpressionEvaluationResult.failure(List.of(diagnostic(code, node.startPosition(),
                node.endPosition(), arguments)), session.trace(), exception);
    }

    /** 创建不包含建议表达式的固定运行期错误诊断。 */
    private static RuleDiagnostic diagnostic(RuleDiagnosticCode code, int start, int end,
            List<Object> arguments) {
        return new RuleDiagnostic(code, DiagnosticSeverity.ERROR, DiagnosticPhase.RUNTIME,
                start, end, arguments, null);
    }

    /** 截断可进入诊断参数的标识文本。 */
    private static String bounded(String value) {
        int maximum = RuleDiagnostic.MAX_ARGUMENT_STRING_LENGTH;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    /** 区分标量中间值与仅供 IN 使用的列表中间值。 */
    private static final class RuntimeValue {
        /** 标量值；列表中间值时为 {@code null}。 */
        private final FactValue value;

        /** IN 右侧列表；标量中间值时为 {@code null}。 */
        private final List<FactValue> list;

        /** 创建恰好包含一种形态的运行时中间值。 */
        private RuntimeValue(FactValue value, List<FactValue> list) {
            this.value = value;
            this.list = list;
        }

        /** 包装标量中间值。 */
        static RuntimeValue value(FactValue value) { return new RuntimeValue(value, null); }

        /** 包装 IN 右侧列表中间值。 */
        static RuntimeValue list(List<FactValue> values) { return new RuntimeValue(null, values); }
    }

    /** 显式求值栈中的节点、阶段和已完成子值。 */
    private static final class EvaluationFrame {
        /** 当前正在执行的 AST 节点。 */
        private final AstNode node;

        /** 已完成的子节点值。 */
        private final List<RuntimeValue> values = new ArrayList<>();

        /** 当前节点下一步需要处理的子节点或阶段。 */
        private int stage;

        /** 创建尚未处理任何子节点的求值帧。 */
        private EvaluationFrame(AstNode node) {
            this.node = node;
        }
    }

    /** 不生成堆栈文本的内部控制异常，携带结构化失败信息。 */
    private static final class EvaluationFailure extends RuntimeException {
        /** 对外稳定诊断码。 */
        private final RuleDiagnosticCode code;

        /** 失败对应的 AST 范围。 */
        private final AstNode node;

        /** 已限制长度的安全诊断参数。 */
        private final List<Object> arguments;

        /** 保留原因链但不拼接原因消息的框架异常。 */
        private final RuleEngineException frameworkException;

        /** 创建单次求值内部的结构化控制异常。 */
        private EvaluationFailure(RuleDiagnosticCode code, AstNode node,
                List<Object> arguments, RuleEngineException frameworkException) {
            super(null, null, false, false);
            this.code = code;
            this.node = node;
            this.arguments = arguments;
            this.frameworkException = frameworkException;
        }
    }
}

/**
 * 事实值与类型描述之间的集中运行期兼容工具。
 */
final class FactValueTypes {
    /** 工具类不允许实例化。 */
    private FactValueTypes() { }

    /** 判断运行时事实值是否满足编译期类型及可空性。 */
    static boolean isAssignable(FactValue value, TypeDescriptor expected) {
        if (value.kind() == FactKind.NULL) return expected.nullable();
        TypeDescriptor actual = typeOf(value);
        if (actual.kind() == TypeKind.ARRAY && expected.kind() == TypeKind.ARRAY) {
            ArrayFactValue array = (ArrayFactValue) value;
            return array.values().stream().allMatch(element -> isAssignable(element, expected.elementType()));
        }
        return TypeCompatibility.isAssignable(actual, expected);
    }

    /** 将运行时事实值结构映射为类型目录描述。 */
    static TypeDescriptor typeOf(FactValue value) {
        TypeKind kind = switch (value.kind()) {
            case NULL -> TypeKind.NULL;
            case STRING -> TypeKind.STRING;
            case BOOLEAN -> TypeKind.BOOLEAN;
            case INTEGER -> TypeKind.INTEGER;
            case DECIMAL -> TypeKind.DECIMAL;
            case DATE -> TypeKind.DATE;
            case DATE_TIME -> TypeKind.DATE_TIME;
            case INSTANT -> TypeKind.INSTANT;
            case OBJECT -> TypeKind.OBJECT;
            case ARRAY -> TypeKind.ARRAY;
        };
        if (kind == TypeKind.ARRAY) {
            ArrayFactValue array = (ArrayFactValue) value;
            TypeDescriptor element = array.values().isEmpty()
                    ? TypeCompatibility.unknown() : typeOf(array.values().get(0));
            return TypeDescriptor.array(element, false);
        }
        if (kind == TypeKind.OBJECT) return TypeDescriptor.object(false);
        return TypeDescriptor.scalar(kind, kind == TypeKind.NULL);
    }
}
