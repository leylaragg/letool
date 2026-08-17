package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 在发布阶段解析 XML 模板集合和片段引用图。
 *
 * @author leyland
 */
public final class XmlTemplateSetCompiler {

    /** 宿主已经配置好格式化器、表达式和标签扩展的单模板编译器。 */
    private final XmlTemplateCompiler templateCompiler;

    /** 约束集合解析和引用展开规模的治理器。 */
    private final XmlTemplateSetGovernor governor;

    /** 使用 XML 模块的默认能力编译模板集合。 */
    public XmlTemplateSetCompiler() {
        this(new XmlTemplateCompiler(), XmlTemplateSetGovernor.standard());
    }

    /**
     * 使用宿主配置好的单模板编译器，让集合编译沿用已有扩展能力。
     *
     * @param templateCompiler XML 单模板编译器
     */
    public XmlTemplateSetCompiler(XmlTemplateCompiler templateCompiler) {
        this(templateCompiler, XmlTemplateSetGovernor.standard());
    }

    /**
     * 组合单模板编译器和集合治理器。
     *
     * @param templateCompiler XML 单模板编译器
     * @param governor 集合容量治理器
     */
    XmlTemplateSetCompiler(XmlTemplateCompiler templateCompiler, XmlTemplateSetGovernor governor) {
        this.templateCompiler = Objects.requireNonNull(templateCompiler, "templateCompiler 不能为空");
        this.governor = Objects.requireNonNull(governor, "governor 不能为空");
    }

    /**
     * 编译一个不可变模板集合。
     *
     * @param templateSet 已完成基础治理的模板集合
     * @return 可并发复用的 XML 编译快照
     */
    public CompiledXmlTemplateSet compile(TemplateSet templateSet) {
        Objects.requireNonNull(templateSet, "templateSet 不能为空");
        Map<String, TemplateDefinition> definitions = definitions(templateSet);
        Map<String, ParsedXmlTemplate> parsed = parseXmlDefinitions(definitions);
        Map<String, List<String>> references = collectAndValidateReferences(templateSet, definitions, parsed);
        List<String> fragmentOrder = fragmentOrder(definitions, references);

        // 片段按依赖顺序编译，后编译的片段可以直接复用已经闭合的下游结果。
        Map<String, CompiledXmlFragment> fragments = new LinkedHashMap<>();
        for (String code : fragmentOrder) {
            ParsedXmlTemplate source = parsed.get(code);
            CompiledXmlNode resolved = resolveIncludes(source.root(), fragments);
            fragments.put(code, templateCompiler.compileFragment(source, resolved));
        }

        Map<String, CompiledXmlTemplate> documents = new TreeMap<>();
        for (Map.Entry<String, ParsedXmlTemplate> entry : parsed.entrySet()) {
            if (entry.getValue().type() != TemplateType.DOCUMENT) {
                continue;
            }
            CompiledXmlNode resolved = resolveIncludes(entry.getValue().root(), fragments);
            CompiledXmlTemplate document = templateCompiler.compileDocument(entry.getValue(), resolved);
            // 同一个片段可以出现多次，最终规模必须按每次出现后的真实结构计算。
            governor.checkExpandedDocument(document.root());
            documents.put(entry.getKey(), document);
        }
        return new CompiledXmlTemplateSet(templateSet.version(), templateSet.digest(), documents);
    }

    /**
     * 将集合定义复制到按代码排序的本地视图，保证后续结果稳定。
     *
     * @param templateSet 待编译模板集合
     * @return 按模板代码排序的定义
     */
    private Map<String, TemplateDefinition> definitions(TemplateSet templateSet) {
        Map<String, TemplateDefinition> definitions = new TreeMap<>();
        for (TemplateDefinition definition : templateSet.definitions()) {
            definitions.put(definition.template().templateCode(), definition);
        }
        return definitions;
    }

    /**
     * 解析本模块负责的 XML 定义，同时累计整个集合的原始节点数。
     *
     * @param definitions 模板集合定义
     * @return 按模板代码排序的 XML 解析结果
     */
    private Map<String, ParsedXmlTemplate> parseXmlDefinitions(Map<String, TemplateDefinition> definitions) {
        Map<String, ParsedXmlTemplate> parsed = new TreeMap<>();
        long totalNodes = 0;
        for (Map.Entry<String, TemplateDefinition> entry : definitions.entrySet()) {
            PrintTemplate template = entry.getValue().template();
            if (!TemplateFormat.LETOOL_XML.equals(template.templateFormat())) {
                continue;
            }
            ParsedXmlTemplate source = templateCompiler.parse(entry.getValue());
            totalNodes += source.nodeCount();
            governor.checkRawNodes(totalNodes);
            parsed.put(entry.getKey(), source);
        }
        return parsed;
    }

    /**
     * 收集全部 include，并确认每个目标都满足片段引用契约。
     *
     * @param templateSet 当前模板集合
     * @param definitions 集合内的全部模板定义
     * @param parsed 已解析的 XML 定义
     * @return 每个 XML 定义直接引用的片段代码
     */
    private Map<String, List<String>> collectAndValidateReferences(
            TemplateSet templateSet,
            Map<String, TemplateDefinition> definitions,
            Map<String, ParsedXmlTemplate> parsed) {
        Map<String, List<String>> references = new TreeMap<>();
        for (Map.Entry<String, ParsedXmlTemplate> entry : parsed.entrySet()) {
            Set<String> targets = new LinkedHashSet<>();
            collectReferences(entry.getValue().root(), targets);
            List<String> sortedTargets = targets.stream().sorted().toList();
            references.put(entry.getKey(), sortedTargets);
            for (String targetCode : sortedTargets) {
                TemplateDefinition target = definitions.get(targetCode);
                if (target == null) {
                    throw PrintCompilationException.invalid(
                            entry.getKey() + "：include 目标不存在：" + targetCode);
                }
                if (target.type() != TemplateType.FRAGMENT) {
                    throw PrintCompilationException.invalid(
                            entry.getKey() + "：include 目标必须为 FRAGMENT：" + targetCode);
                }
                validateTargetVersions(templateSet.version(), entry.getValue().template(), target.template());
            }
        }
        return references;
    }

    /**
     * 检查引用两端是否属于同一套 XML、DSL 和上下文契约。
     *
     * @param setVersion 当前集合版本
     * @param source 发起 include 的模板
     * @param target 被引用的片段模板
     */
    private void validateTargetVersions(long setVersion, PrintTemplate source, PrintTemplate target) {
        if (!TemplateFormat.LETOOL_XML.equals(target.templateFormat())) {
            throw PrintCompilationException.invalid(source.templateCode()
                    + "：include 目标不是 letool-xml：" + target.templateCode());
        }
        if (source.templateSetVersion() != setVersion
                || target.templateSetVersion() != setVersion
                || source.dslVersion() != target.dslVersion()
                || source.contextVersion() != target.contextVersion()) {
            throw PrintCompilationException.invalid(source.templateCode()
                    + "：include 目标版本不一致：" + target.templateCode());
        }
    }

    /**
     * 从已受深度限制的节点树中收集直接和嵌套引用。
     *
     * @param node 当前编译节点
     * @param references 用于去重的引用集合
     */
    private void collectReferences(CompiledXmlNode node, Set<String> references) {
        if ("include".equals(node.name())) {
            references.add(node.attributes().get("template"));
        }
        for (CompiledXmlNode child : node.children()) {
            collectReferences(child, references);
        }
    }

    /**
     * 检查全部 XML 片段的循环引用，并生成依赖优先的编译顺序。
     *
     * @param definitions 集合内的全部模板定义
     * @param references 每个 XML 定义的直接引用
     * @return 依赖优先的片段代码
     */
    private List<String> fragmentOrder(
            Map<String, TemplateDefinition> definitions,
            Map<String, List<String>> references) {
        Map<String, VisitState> states = new TreeMap<>();
        List<String> order = new ArrayList<>();
        for (Map.Entry<String, TemplateDefinition> entry : definitions.entrySet()) {
            if (entry.getValue().type() == TemplateType.FRAGMENT
                    && TemplateFormat.LETOOL_XML.equals(entry.getValue().template().templateFormat())) {
                visitFragment(entry.getKey(), references, states, order);
            }
        }
        return order;
    }

    /**
     * 从一个片段开始迭代遍历引用图，避免深引用链占用调用栈。
     *
     * @param start 起始片段代码
     * @param references 每个 XML 定义的直接引用
     * @param states 已访问片段的状态
     * @param order 依赖优先的编译顺序
     */
    private void visitFragment(
            String start,
            Map<String, List<String>> references,
            Map<String, VisitState> states,
            List<String> order) {
        if (states.get(start) == VisitState.VISITED) {
            return;
        }
        Deque<GraphFrame> stack = new ArrayDeque<>();
        states.put(start, VisitState.VISITING);
        stack.push(new GraphFrame(start, references.getOrDefault(start, List.of())));
        while (!stack.isEmpty()) {
            GraphFrame frame = stack.peek();
            if (!frame.hasNext()) {
                stack.pop();
                states.put(frame.code(), VisitState.VISITED);
                order.add(frame.code());
                continue;
            }
            String target = frame.next();
            VisitState targetState = states.get(target);
            if (targetState == VisitState.VISITING) {
                throw PrintCompilationException.invalid("XML 片段存在循环引用：" + cyclePath(stack, target));
            }
            if (targetState == VisitState.VISITED) {
                continue;
            }
            states.put(target, VisitState.VISITING);
            stack.push(new GraphFrame(target, references.getOrDefault(target, List.of())));
            governor.checkIncludeDepth(stack.size());
        }
    }

    /**
     * 从遍历栈生成长度受控的循环路径，不回显模板正文。
     *
     * @param stack 当前图遍历栈
     * @param target 再次遇到的片段代码
     * @return 可安全展示的循环路径
     */
    private String cyclePath(Deque<GraphFrame> stack, String target) {
        List<String> path = new ArrayList<>();
        Iterator<GraphFrame> frames = stack.descendingIterator();
        while (frames.hasNext()) {
            path.add(frames.next().code());
        }
        path.add(target);
        int start = Math.max(path.indexOf(target), 0);
        List<String> cycle = path.subList(start, path.size());
        if (cycle.size() > 16) {
            cycle = new ArrayList<>(cycle.subList(0, 15));
            cycle.add("...");
        }
        String detail = String.join(" -> ", cycle);
        return detail.length() <= 512 ? detail : detail.substring(0, 509) + "...";
    }

    /**
     * 将节点树中的 include 替换为已经编译的共享片段引用。
     *
     * @param node 当前节点
     * @param fragments 已完成编译的片段
     * @return 解析引用后的不可变节点
     */
    private CompiledXmlNode resolveIncludes(
            CompiledXmlNode node,
            Map<String, CompiledXmlFragment> fragments) {
        if ("include".equals(node.name())) {
            String target = node.attributes().get("template");
            CompiledXmlFragment fragment = fragments.get(target);
            if (fragment == null) {
                throw PrintCompilationException.invalid("include 目标尚未完成编译：" + target);
            }
            return node.withChildrenAndFragment(List.of(), fragment);
        }
        List<CompiledXmlNode> children = new ArrayList<>(node.children().size());
        for (CompiledXmlNode child : node.children()) {
            children.add(resolveIncludes(child, fragments));
        }
        return node.withChildrenAndFragment(children, node.includedFragment());
    }

    /** 图节点的访问状态。 */
    private enum VisitState {
        VISITING,
        VISITED
    }

    /**
     * 显式保存一次图遍历位置，避免深引用链占用调用栈。
     *
     * @author leyland
     */
    private static final class GraphFrame {

        /** 当前片段代码。 */
        private final String code;

        /** 当前片段的有序依赖。 */
        private final List<String> targets;

        /** 下一个待访问依赖的位置。 */
        private int index;

        /**
         * 记录片段和它的有序依赖。
         *
         * @param code 当前片段代码
         * @param targets 当前片段的有序依赖
         */
        private GraphFrame(String code, List<String> targets) {
            this.code = code;
            this.targets = targets;
        }

        /** @return 当前片段代码 */
        private String code() {
            return code;
        }

        /** @return 是否仍有未访问依赖 */
        private boolean hasNext() {
            return index < targets.size();
        }

        /** @return 下一个依赖代码 */
        private String next() {
            return targets.get(index++);
        }
    }
}
