# letool-starter-print-xml

`letool-starter-print-xml` 是 Letool 动态打印框架的受控 XML DSL 模块。它提供无 DOM 暴露的安全编译入口，并把静态或受限动态标签绑定为核心 `DocumentModel`。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-print-xml</artifactId>
    <version>${letool.version}</version>
</dependency>
```

该模块依赖 `letool-starter-print` 和模板集合模块 `letool-starter-print-template`，不依赖 Spring、数据库、EDC 业务模型、OpenHTMLToPDF、PDFBox、docx4j、JasperReports 或表达式引擎。

## 基础用法

```java
PrintTemplate source = new PrintTemplate(
        "contract",
        TemplateFormat.LETOOL_XML,
        1,
        7,
        1,
        xmlBytes);

CompiledXmlTemplate compiled = new XmlTemplateCompiler().compile(source);
DocumentModel document = new XmlTemplateBinder().bind(compiled, printContext);
```

编译快照可以在上下文版本匹配的前提下重复绑定，不保存 DOM、StAX、业务 POJO 或 Spring 对象。

## 模板片段与 include

模板集合可以声明块级 `FRAGMENT`，完整文档和其他片段通过空元素 `include` 引用：

```xml
<fragment xmlns="https://leyland.github.io/letool/print/v1">
    <heading><field path="report.title"/></heading>
    <paragraph>由共享片段生成</paragraph>
</fragment>
```

```xml
<document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
    <page>
        <include template="common-header"/>
    </page>
</document>
```

`include` 可放在 `fragment/page/section/cell/if/for-each` 的块级位置，不能放在标题、段落、表格行或 `header/body` 行域。片段只能读取根 `PrintContext` 和自己声明的循环变量，不会隐式捕获引用位置的 `$变量`。

集合必须通过 `XmlTemplateSetCompiler` 编译；独立的 `XmlTemplateCompiler.compile()` 遇到 `include` 会明确失败：

```java
XmlTemplateSetCompiler compiler = new XmlTemplateSetCompiler(xmlTemplateCompiler);
CompiledXmlTemplateSet compiledSet = compiler.compile(templateSet);
CompiledXmlTemplate compiled = compiledSet.require("patient-report");
```

引用目标必须是同一 `TemplateSet` 中的 `letool-xml` `FRAGMENT`，并保持 DSL、集合和上下文版本一致。编译阶段会检查目标缺失、用途错误、循环引用、引用深度、集合节点总量，以及按引用出现次数计算的展开节点和结构深度。绑定阶段继续沿用同一个 Governor 统计动态操作、最终节点和文本容量。

## 基础与动态标签

```xml
<document xmlns="https://leyland.github.io/letool/print/v1"
          context-version="1"
          title="合同"
          author="Letool"
          language="zh-CN">
    <page size="A4" orientation="portrait" margin="20mm">
        <section id="summary">
            <heading id="title" level="1">合同摘要</heading>
            <paragraph id="intro">投保人：<field path="policy.holder.name"/></paragraph>
            <if path="policy.active" operator="truthy">
                <paragraph>保单有效</paragraph>
            </if>
            <for-each items="policy.coverages" var="coverage">
                <paragraph><field path="$coverage.name"/></paragraph>
            </for-each>
            <page-break/>
        </section>
    </page>
</document>
```

当前支持：

- `document`：上下文版本以及可选标题、作者、语言；
- `page`：A4/LETTER、portrait/landscape 和统一毫米边距；
- `section`、`heading`、`paragraph`、`text` 和 `page-break`；
- 行内 `field`，只输出字符串、数字、布尔或显式空值；
- 块级 `if`，支持存在性、布尔、相等和精确数字大小比较；
- 块级 `for-each`，支持嵌套词法作用域和 `$变量` 路径；
- `table/header/body/row/cell`，支持动态完整行和严格跨行跨列网格；
- 只保存逻辑资源引用的 `image` 描述；
- 行内 `bookmark` 和文档内 `link`；
- 可选 field 格式化计划和内置 `number/date/datetime` 格式化器；
- 显式注册的条件表达式和可信自定义标签 SPI；
- 块级 `fragment/include` 引用图和闭合作用域绑定；
- 标签、属性、父子关系、ID、标题层级、节点数量、深度和文本长度校验。

## 数据路径与空值

- 根路径使用 `policy.holder.name`，循环变量路径使用 `$coverage.name`；
- 路径只允许对象字段，不支持下标、通配符、方法、类名或任意表达式；
- 缺失路径绑定失败，显式 JSON `null` 作为已存在空值；
- `field` 遇到显式空值输出空文本，对象和数组不能直接输出；
- `for-each` 遇到显式空值按空数组处理，非数组值绑定失败；
- 循环变量只在循环体内有效，内层可以读取外层变量但禁止重名；
- 循环后代禁止声明静态 `id`，因此不能在循环中声明表格 ID、图片 ID 或书签 ID；
- `section` 的动态内容全部展开为空时自动剪枝，不生成非法空章节。

结构化条件示例：

```xml
<if path="policy.amount"
    operator="gte"
    value="1000.00"
    value-type="number">
    <paragraph>达到保额门槛</paragraph>
</if>
```

`value-type` 支持 `string/number/boolean/null`，默认 `string`。数字使用十进制精确比较。

## 表格、图片与文档内导航

```xml
<document xmlns="https://leyland.github.io/letool/print/v1" context-version="1">
    <page>
        <image id="logo"
               resource-id="brand.logo"
               alt="公司标识"
               width="30mm"
               height="12mm"/>
        <table id="items">
            <header>
                <row>
                    <cell><paragraph>名称</paragraph></cell>
                    <cell><paragraph>金额</paragraph></cell>
                </row>
            </header>
            <body>
                <for-each items="order.items" var="item">
                    <row>
                        <cell><paragraph><field path="$item.name"/></paragraph></cell>
                        <cell><paragraph><field path="$item.amount" formatter="number"/></paragraph></cell>
                    </row>
                </for-each>
            </body>
        </table>
        <paragraph><bookmark id="summary" label="汇总"/><link target="summary">返回汇总</link></paragraph>
    </page>
</document>
```

- `table` 的 `header` 可省略，`body` 必须且只能有一个；`header` 必须位于 `body` 前；
- `header/body` 内只允许 `row/if/for-each`，动态结构必须产生完整 `row`；
- `cell` 支持 `row-span/col-span`，缺省均为 1，内部可包含普通块节点和嵌套表格；
- 表头与表体是独立跨行分区，不能跨区合并；绑定后空表会剪枝，仅表头表格可以保留；
- `image` 的 `resource-id` 与 `resource-path` 二选一；后者读取受限数据路径中的非空字符串；
- 图片只保存 `ImageNode` 描述，不读取文件、classpath、URL 或字节，不探测 MIME；
- `bookmark` 建立静态目标，`link` 只能以文本、`text` 和 `field` 作为标签；
- 绑定完成后统一校验 ID 全局唯一和链接目标存在性。

图片实际资源解析属于后续独立扩展。渲染器没有配置受控资源解析能力时，应明确报告能力不支持，不能静默丢弃图片。

## 字段格式化器

```xml
<field path="order.total" formatter="number">
    <format-option name="pattern" value="#,##0.00"/>
    <format-option name="locale" value="zh-CN"/>
</field>
```

内置格式化器：

- `number`：支持 `pattern/locale/rounding-mode`；
- `date`：支持 `pattern/input-pattern/locale/zone-id`；
- `datetime`：支持 `pattern/input-pattern/locale/zone-id`。

未配置 `locale` 时使用 `Locale.ROOT`，未配置 `zone-id` 时使用 UTC。日期字符串缺省使用 ISO 语义，数字时间值按 epoch millis 解释。显式 JSON `null` 输出空文本且不会调用格式化器。

自定义格式化器通过显式注册表注入：

```java
PrintFormatterRegistry registry = new PrintFormatterRegistry(List.of(myFormatter));
CompiledXmlTemplate compiled = new XmlTemplateCompiler(registry).compile(source);
```

`PrintValueFormatter` 在编译阶段把静态选项转换为 `PrintFormatPlan`。计划会固化在编译快照中，绑定阶段不会再次查询注册表。格式化器和计划都应当无状态或线程安全，异常信息不得包含业务值。

## 条件表达式扩展

默认结构化条件始终保留且不需要表达式引擎。可信 Java 代码可以显式注册额外条件语言：

```java
PrintConditionExpression expression = new MyConditionExpression();
PrintExpressionRegistry expressions = new PrintExpressionRegistry(List.of(expression));

XmlTemplateCompiler compiler = new XmlTemplateCompiler(
        BuiltInPrintFormatters.registry(),
        expressions,
        new PrintTagRegistry(List.of()));
```

XML 必须显式声明语言和表达式正文：

```xml
<if expression-language="my-language" test="policy-active">
    <paragraph>保单已生效</paragraph>
</if>
```

- `expression-language/test` 必须同时出现，并与 `path/operator/value/value-type` 互斥；
- 未注册语言在模板编译期失败，不会降级为结构化条件；
- `PrintConditionExpression` 在编译期生成可并发复用的 `PrintExpressionPlan`；
- 求值计划只接收 `PrintDataView`，其中根 JSON 和循环变量均为防御性副本；
- 表达式编译和求值异常会转换为安全框架异常，不回显正文、业务值或实现消息；
- 每次求值计入动态操作 Governor，表达式正文受中央长度限制。

本模块不内置任何通用表达式语言。显式引入独立的 `letool-starter-print-expression-spel` 并注册 `RestrictedSpelConditionExpression` 后，XML 才能使用 `expression-language="spel"`；不引入或不注册时，模板在编译阶段失败。完整语法和安全边界参见 [`letool-starter-print-expression-spel`](../letool-starter-print-expression-spel/README.md)。

## 可信自定义标签

自定义标签只能由可信 Java 代码注册，XML 不能填写处理器类名、Bean 名或类路径：

```java
PrintTagHandler notice = new NoticeTagHandler();
PrintTagRegistry tags = new PrintTagRegistry(List.of(notice));

XmlTemplateCompiler compiler = new XmlTemplateCompiler(
        BuiltInPrintFormatters.registry(),
        new PrintExpressionRegistry(List.of()),
        tags);
```

处理器声明：

- 稳定的小写标签名和静态属性白名单；
- `BLOCK/INLINE` 放置位置；
- `EMPTY/BLOCKS/INLINE` 子节点内容模型；
- 编译期 `PrintTagPlan`，用于绑定时返回一个核心 `DocumentNode`。

编译上下文只暴露白名单属性与安全位置说明；绑定上下文只暴露只读 `PrintDataView` 和框架已经绑定好的受控子节点。处理器不接触 DOM、StAX、业务 POJO、Spring 容器、内部 `BindingScope` 或 Governor。

扩展返回结果仍由框架统一执行：

- 块/行内节点类型校验；
- 完整返回树的节点、表格行、单元格和文本容量计数；
- 循环后代 ID 禁令；
- 最终节点 ID 唯一性和内部链接目标校验；
- 异常信息脱敏。

自定义标签不能覆盖内置标签，不能作为 `link` 标签内容，也不能绕过 `table/header/body/row/cell` 的结构约束。实际资源解析仍属于后续独立扩展。

## 安全边界

- 只接受固定 DSL 命名空间和严格 UTF-8。
- 在解析前拒绝 DOCTYPE、ENTITY、XInclude、外部 Schema 和可执行表达式标记。
- StAX 显式关闭 DTD、外部实体和实体替换，并使用拒绝型外部资源解析器。
- 未知标签、属性和错误结构在编译阶段失败。
- XML 不能调用 Java、反射、Spring Bean、SQL、脚本、文件系统或网络。
- 用户可见编译错误只包含模板代码、标签及安全行列位置，不回显模板正文或外部资源地址。
- 动态绑定限制路径长度、路径段数、循环嵌套、单循环元素、累计动态操作、生成节点和生成文本总量。
- 模板集合编译限制原始节点总量、引用链深度、展开节点总量和最终结构深度。

宿主应用仍负责权限、数据查询、脱敏、字典翻译和业务计算；本模块不会为了 EDC 或其他首个使用方加入领域定制逻辑。
