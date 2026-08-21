# 动态打印扩展开发指南

Letool 把模板语法、通用文档模型、输出实现和模板存储分成独立扩展层。先选择与问题匹配的接口，不要把业务查询、权限或某个项目的特殊规则塞进通用扩展。

在 Spring Boot 项目中，把扩展实现声明为 Bean 即可。Starter 会按 Spring 顺序收集实现，再创建不可变注册表；相同标签、格式化器、表达式语言、模板格式或输出格式重复时会阻止启动。

## 选择扩展点

| 需求 | 接口 |
|---|---|
| 增加受控 XML 标签 | `PrintTagHandler` |
| 增加字段显示格式 | `PrintValueFormatter` |
| 增加条件表达式语言 | `PrintConditionExpression` |
| 把 `DocumentModel` 导出为共享语义格式 | `DocumentRenderer` |
| 接入独立模板和报表模型 | `PrintPipeline` |
| 持久化模板集合并切换版本 | `TemplateRepository` |

如果新格式无法稳定表达 `DocumentModel` 的章节、段落、表格和导航语义，应实现独立 `PrintPipeline`。JasperReports 适配属于这一层，不应修改 Letool XML 或 PDF 主链路。

## 自定义标签

下面的 `badge` 是行内标签，允许一个静态 `prefix` 属性，并接收框架已经绑定完成的行内子节点：

```java
public final class BadgeTagHandler implements PrintTagHandler {

    @Override
    public String tagName() {
        return "badge";
    }

    @Override
    public TagPlacement placement() {
        return TagPlacement.INLINE;
    }

    @Override
    public TagContentModel contentModel() {
        return TagContentModel.INLINE;
    }

    @Override
    public Set<String> allowedAttributes() {
        return Set.of("prefix");
    }

    @Override
    public PrintTagPlan compile(TagCompileContext context) {
        String prefix = context.attribute("prefix").orElse("");
        return PrintTagPlan.of(TextNode.class,
                binding -> new TextNode(prefix + binding.inlineChildren().stream()
                        .map(TextNode.class::cast)
                        .map(TextNode::text)
                        .reduce("", String::concat)));
    }
}
```

模板用法：

```xml
<paragraph><badge prefix="[重点] ">金额：<field path="amount"/></badge></paragraph>
```

处理器只能读取白名单属性和 `PrintDataView` 的标准 JSON 副本，不能通过 XML 选择 Java 类或 Bean。计划需要声明每一种可能返回的具体节点类型，父接口或抽象类型不能代替这份清单。返回节点仍会经过位置、循环 ID、导航、节点数和文本量检查；不要把预绑定子树重复插入来绕开容量统计。

## 自定义格式化器

格式化器在编译阶段冻结静态选项，运行阶段只处理非空 JSON 值：

```java
public final class PrefixFormatter implements PrintValueFormatter {

    @Override
    public String name() {
        return "prefix";
    }

    @Override
    public PrintFormatPlan compile(Map<String, String> options, FormatCompileContext context) {
        String prefix = options.getOrDefault("value", "");
        return value -> prefix + value.asText();
    }
}
```

```xml
<field path="orderNo" formatter="prefix">
    <format-option name="value" value="NO-"/>
</field>
```

`compile` 应拒绝未知、重复或不合法选项；返回的 `PrintFormatPlan` 必须可并发复用。格式化结果会计入绑定文本上限。实现异常会被转换成安全错误，因此不要依赖异常正文向模板作者传递业务数据。

## 自定义条件表达式

下面示例只把表达式正文当作根 JSON 的布尔字段名，不执行脚本：

```java
public final class FlagConditionExpression implements PrintConditionExpression {

    @Override
    public String language() {
        return "flag";
    }

    @Override
    public PrintExpressionPlan compile(ExpressionCompileContext context) {
        String field = context.expression();
        if (!field.matches("[a-z][a-zA-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("flag 表达式必须是安全字段名");
        }
        TemplateInspectionContribution inspection = TemplateInspectionContribution.builder()
                .dataPath(field)
                .build();
        return PrintExpressionPlan.of(inspection,
                evaluation -> evaluation.data().root().path(field).asBoolean(false));
    }
}
```

```xml
<if expression-language="flag" test="approved">
    <then><paragraph>已批准</paragraph></then>
</if>
```

表达式提供方必须自己定义白名单语法、源码深度、求值预算和只读访问策略。不要直接暴露通用脚本引擎、反射、Spring Bean、文件或网络访问。需要 SpEL 时优先使用框架提供的受限模块。

## 自定义文档渲染器

共享 `DocumentModel` 语义的新输出实现 `DocumentRenderer`。下面是只支持段落和文本的最小纯文本渲染器：

```java
public final class PlainTextRenderer implements DocumentRenderer {

    private static final OutputFormat TEXT =
            new OutputFormat("text", "text/plain", "txt");

    private static final OutputCapability CAPABILITY = new OutputCapability(
            Set.of(ParagraphNode.class, TextNode.class),
            Set.of());

    @Override
    public OutputFormat outputFormat() {
        return TEXT;
    }

    @Override
    public OutputCapability capability() {
        return CAPABILITY;
    }

    @Override
    public PrintResult render(
            DocumentModel document, RenderOptions options, PrintOutput output) {
        CAPABILITY.requireSupports(document);
        String text = DocumentTraversal.depthFirst(document).stream()
                .filter(TextNode.class::isInstance)
                .map(TextNode.class::cast)
                .map(TextNode::text)
                .collect(Collectors.joining(System.lineSeparator()));
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        output.write(content);
        return output.complete(TEXT, Map.of("encoding", "UTF-8"));
    }
}
```

能力集合必须列出实际支持的具体节点类型和 `DocumentFeature`。第二个集合为空表示渲染器不接受多页面序列、页眉页脚、逻辑页码、命名样式等高级语义；框架会在调用前再次检查，渲染器不能静默忽略未知节点或文档能力。示例为了简洁先构造小文本；大产物应分批写入 `PrintOutput`，需要临时文件时还要在实际写入处治理工作区总量。

## 自定义顶层管线

具有独立模板语义的格式实现 `PrintPipeline`。下面的最小示例把受控纯文本模板原样输出，展示模板格式、输出格式和容量契约：

```java
public final class PlainTemplatePipeline implements PrintPipeline {

    private static final TemplateFormat TEMPLATE = new TemplateFormat("plain-template");
    private static final OutputFormat TEXT = new OutputFormat("text", "text/plain", "txt");

    @Override
    public TemplateFormat templateFormat() {
        return TEMPLATE;
    }

    @Override
    public Set<OutputFormat> supportedOutputs() {
        return Set.of(TEXT);
    }

    @Override
    public PrintResult render(PrintRequest request, PrintOutput output) {
        byte[] content = request.template().content();
        output.write(content);
        return output.complete(TEXT, Map.of("source", "plain-template"));
    }
}
```

第三方报表管线还应完成模板编译缓存、数据填充、导出器选择、分页/字节限制、临时资源隔离和异常转换。捕获第三方异常后使用带原因链的框架异常，用户可见参数只能包含稳定模板格式、输出格式或安全分类。

## 自定义模板仓库

默认 `InMemoryTemplateRepository` 适合启动期固定模板和测试。业务需要数据库、对象存储或配置中心时，可以实现 `TemplateRepository`。下面的委托实现列出完整契约，替换委托部分即可接入持久化：

```java
public final class DelegatingTemplateRepository implements TemplateRepository {

    private final TemplateRepository delegate = new InMemoryTemplateRepository();

    @Override
    public Optional<TemplateSet> find(long version) {
        return delegate.find(version);
    }

    @Override
    public Optional<TemplateSet> current() {
        return delegate.current();
    }

    @Override
    public TemplateSet publish(TemplateSet templateSet) {
        return delegate.publish(templateSet);
    }

    @Override
    public TemplateSet publishAndActivate(TemplateSet templateSet) {
        return delegate.publishAndActivate(templateSet);
    }

    @Override
    public TemplateSet activate(long version) {
        return delegate.activate(version);
    }
}
```

持久化实现必须保证：

- 同一版本不可覆盖；
- `publishAndActivate` 对读者表现为一次原子切换；
- `current()` 返回一个完整不可变集合，不拼接不同版本；
- 多节点环境自行提供数据库事务、CAS 或分布式一致性；
- 仓库只保存模板，不绕过 `TemplateSetPublisher` 的发布校验；
- 仓库异常不回显 SQL、连接串、绝对路径或模板正文。

框架不提供数据库表、管理 Controller 或配置中心热切换实现，这些属于宿主基础设施。

## 线程安全与资源所有权

扩展实例会被多个请求并发调用。处理器、格式化器、表达式计划、渲染器和管线应无状态，或把可变状态限制在方法局部。编译计划可以缓存静态配置，但不能保存某次请求的 `PrintContext`、`JsonNode`、业务对象或临时路径。

调用方拥有传入请求和业务资源；框架在公开边界使用防御性副本。扩展自己打开的流、第三方文档和临时文件必须在成功与失败路径关闭。不要关闭调用方交给其他组件管理的流，也不要把未关闭资源放进长期缓存。

## 异常与容量责任

可信 Java 扩展仍属于框架边界的一部分：

- 使用 Letool 打印异常表达可预期校验或容量失败；
- 第三方故障保留 `cause`，但用户消息不包含第三方原文；
- 不把模板正文、业务值、表达式、SQL、URL 或绝对路径放进消息参数；
- 标签结果继续接受中央节点、文本、ID 和导航治理；
- 渲染器和独立管线必须在分配资源前执行自己的页数、字节数、步骤数或临时磁盘限制；
- 任何缓存都需要容量、命中统计和并发语义，不能以静态无界 Map 代替。

扩展测试至少应覆盖正常输出、重复注册、并发复用、资源清理、容量恰好上限与超限，以及异常消息不包含构造的敏感标记。
