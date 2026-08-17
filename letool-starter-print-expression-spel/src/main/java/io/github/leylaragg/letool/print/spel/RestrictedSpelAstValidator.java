package io.github.leylaragg.letool.print.spel;

import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BooleanLiteral;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.FloatLiteral;
import org.springframework.expression.spel.ast.Indexer;
import org.springframework.expression.spel.ast.IntLiteral;
import org.springframework.expression.spel.ast.LongLiteral;
import org.springframework.expression.spel.ast.Literal;
import org.springframework.expression.spel.ast.NullLiteral;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpEQ;
import org.springframework.expression.spel.ast.OpGE;
import org.springframework.expression.spel.ast.OpGT;
import org.springframework.expression.spel.ast.OpLE;
import org.springframework.expression.spel.ast.OpLT;
import org.springframework.expression.spel.ast.OpNE;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.RealLiteral;
import org.springframework.expression.spel.ast.StringLiteral;

import java.util.Objects;
import java.util.Set;

/**
 * 按明确节点白名单校验 Spring 表达式树。
 *
 * <p>校验器采用默认拒绝策略。Spring 未来新增的 AST 类型不会自动获得执行权限，必须经过打印框架安全评审并增加测试后才能加入白名单。
 * 实例不保存遍历状态，可以安全并发复用。</p>
 *
 * @author leyland
 */
final class RestrictedSpelAstValidator {

    /** 单个表达式允许的 AST 节点总数。 */
    private static final int MAX_AST_NODES = 128;

    /** 单个表达式允许的 AST 最大深度，根节点深度为一。 */
    private static final int MAX_AST_DEPTH = 32;

    /** 单条连续属性和下标读取链允许的节点数。 */
    private static final int MAX_ACCESS_CHAIN = 32;

    /** 整棵表达式允许的数组下标节点数。 */
    private static final int MAX_INDEXES = 16;

    /** 单个字符串字面量允许的解码后字符数。 */
    private static final int MAX_STRING_LITERAL_CHARACTERS = 1_024;

    /** 单个数字字面量允许的源码字符数。 */
    private static final int MAX_NUMERIC_LITERAL_CHARACTERS = 64;

    /** 允许作为纯值参与比较的精确字面量节点类型。 */
    private static final Set<Class<?>> LITERAL_TYPES = Set.of(
            StringLiteral.class,
            IntLiteral.class,
            LongLiteral.class,
            FloatLiteral.class,
            RealLiteral.class,
            BooleanLiteral.class,
            NullLiteral.class);

    /** 允许的二元布尔和比较运算节点类型。 */
    private static final Set<Class<?>> BINARY_OPERATOR_TYPES = Set.of(
            OpAnd.class,
            OpOr.class,
            OpEQ.class,
            OpNE.class,
            OpLT.class,
            OpLE.class,
            OpGT.class,
            OpGE.class);

    /** 即使同名 JSON 字段存在也不允许模板读取的 Java 元数据名称。 */
    private static final Set<String> JAVA_METADATA_PROPERTIES = Set.of(
            "class",
            "getClass",
            "classLoader",
            "declaringClass");

    /**
     * 创建无状态 AST 校验器。
     */
    RestrictedSpelAstValidator() {
    }

    /**
     * 校验整棵表达式树只包含允许的读取、字面量和条件运算节点。
     *
     * @param root 表达式根节点
     * @throws IllegalArgumentException 表达式包含白名单外节点或非法节点结构时抛出
     */
    void validate(SpelNode root) {
        ValidationState state = new ValidationState();
        validateNode(Objects.requireNonNull(root, "root 不能为空"), 1, state);
    }

    /**
     * 递归校验单个节点及其后代结构。
     *
     * @param node 当前待校验节点
     * @param depth 当前节点深度，根节点为一
     * @param state 当前单次遍历独占的累计容量状态
     * @throws IllegalArgumentException 节点类型、结构或累计容量不符合安全约束时抛出
     */
    private void validateNode(SpelNode node, int depth, ValidationState state) {
        state.enterNode(depth);
        // 必须比较精确运行时类型，避免 Spring 新增子类通过 instanceof 自动进入白名单。
        Class<?> nodeType = node.getClass();
        if (LITERAL_TYPES.contains(nodeType)) {
            requireChildCount(node, 0);
            validateLiteral((Literal) node);
            return;
        }
        if (nodeType == PropertyOrFieldReference.class) {
            validateProperty((PropertyOrFieldReference) node);
            return;
        }
        if (nodeType == Indexer.class) {
            validateIndexer((Indexer) node, depth, state);
            return;
        }
        if (nodeType == CompoundExpression.class) {
            validateCompound(node, depth, state);
            return;
        }
        if (nodeType == OperatorNot.class) {
            requireChildCount(node, 1);
            validateNode(node.getChild(0), depth + 1, state);
            return;
        }
        if (BINARY_OPERATOR_TYPES.contains(nodeType)) {
            requireChildCount(node, 2);
            validateNode(node.getChild(0), depth + 1, state);
            validateNode(node.getChild(1), depth + 1, state);
            return;
        }
        throw unsupportedSyntax();
    }

    /**
     * 校验属性节点没有空安全访问或隐藏后代。
     *
     * @param property 属性读取节点
     * @throws IllegalArgumentException 属性包含后代、空安全访问或 Java 元数据名称时抛出
     */
    private void validateProperty(PropertyOrFieldReference property) {
        requireChildCount(property, 0);
        if (property.isNullSafe()
                || JAVA_METADATA_PROPERTIES.contains(property.getName())) {
            throw unsupportedSyntax();
        }
    }

    /**
     * 校验数组访问只使用非负整数字面量下标。
     *
     * @param indexer 数组访问节点
     * @param depth 当前数组访问节点深度
     * @param state 当前单次遍历独占的累计容量状态
     * @throws IllegalArgumentException 下标结构、类型或累计容量不符合安全约束时抛出
     */
    private void validateIndexer(
            Indexer indexer, int depth, ValidationState state) {
        requireChildCount(indexer, 1);
        if (indexer.isNullSafe() || indexer.getChild(0).getClass() != IntLiteral.class) {
            throw unsupportedSyntax();
        }
        state.enterIndex();
        // 负数会被 Spring 解析为一元减法节点而不是 IntLiteral，因此无需读取或回显源码值。
        validateNode(indexer.getChild(0), depth + 1, state);
    }

    /**
     * 校验连续读取链仅由属性和数组下标组成。
     *
     * @param compound 连续读取节点
     * @param depth 当前连续读取节点深度
     * @param state 当前单次遍历独占的累计容量状态
     * @throws IllegalArgumentException 读取链长度、节点类型或累计容量不符合安全约束时抛出
     */
    private void validateCompound(
            SpelNode compound, int depth, ValidationState state) {
        if (compound.getChildCount() < 2
                || compound.getChildCount() > MAX_ACCESS_CHAIN) {
            throw unsupportedSyntax();
        }
        for (int index = 0; index < compound.getChildCount(); index++) {
            // 连续表达式只允许沿内部 JSON 包装节点读取，不放行方法或其他可执行子节点。
            SpelNode child = compound.getChild(index);
            Class<?> childType = child.getClass();
            if (childType != PropertyOrFieldReference.class
                    && childType != Indexer.class) {
                throw unsupportedSyntax();
            }
            validateNode(child, depth + 1, state);
        }
    }

    /**
     * 校验字面量本身不会绕过模板正文之外的精细容量限制。
     *
     * @param literal 当前字面量节点
     * @throws IllegalArgumentException 字符串或数字字面量超过精细容量上限时抛出
     */
    private void validateLiteral(Literal literal) {
        if (literal.getClass() == StringLiteral.class) {
            Object value = literal.getLiteralValue().getValue();
            if (!(value instanceof String text)
                    || text.length() > MAX_STRING_LITERAL_CHARACTERS) {
                throw unsupportedSyntax();
            }
            return;
        }
        if (literal.isNumberLiteral()
                && literal.getOriginalValue().length()
                > MAX_NUMERIC_LITERAL_CHARACTERS) {
            throw unsupportedSyntax();
        }
    }

    /**
     * 校验节点拥有白名单语义要求的精确子节点数量。
     *
     * @param node 当前节点
     * @param expected 期望子节点数
     * @throws IllegalArgumentException 实际子节点数量与白名单语义不一致时抛出
     */
    private void requireChildCount(SpelNode node, int expected) {
        if (node.getChildCount() != expected) {
            throw unsupportedSyntax();
        }
    }

    /**
     * 创建不包含节点类名、AST 或表达式正文的内部拒绝异常。
     *
     * @return 安全的非法语法异常
     */
    private IllegalArgumentException unsupportedSyntax() {
        return new IllegalArgumentException("条件表达式包含不支持的语法");
    }

    /**
     * 保存一次 AST 遍历的累计容量状态。
     *
     * <p>状态只在单次编译调用栈中使用，不进入可复用表达式计划。</p>
     *
     * @author leyland
     */
    private static final class ValidationState {

        /** 已访问 AST 节点数。 */
        private int nodeCount;

        /** 已访问数组下标节点数。 */
        private int indexCount;

        /**
         * 记录一个 AST 节点，并在修改计数前检查总数和深度。
         *
         * @param depth 当前节点深度
         * @throws IllegalArgumentException AST 节点总数或深度超过安全上限时抛出
         */
        private void enterNode(int depth) {
            if (nodeCount >= MAX_AST_NODES || depth > MAX_AST_DEPTH) {
                throw new IllegalArgumentException("条件表达式结构超过安全限制");
            }
            nodeCount++;
        }

        /**
         * 记录一个数组下标节点，并在修改计数前检查累计上限。
         *
         * @throws IllegalArgumentException 数组下标节点总数超过安全上限时抛出
         */
        private void enterIndex() {
            if (indexCount >= MAX_INDEXES) {
                throw new IllegalArgumentException("条件表达式结构超过安全限制");
            }
            indexCount++;
        }
    }
}
