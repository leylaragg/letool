# 动态打印模板作者指南

本指南面向编写 Letool XML 的模板作者。模板只描述通用文档语义，业务查询、权限、字典翻译和计算应先由宿主应用完成，再通过 `PrintContext` 提供标准 JSON 数据。

## 模板集合

一次发布包含一个或多个完整文档和可复用片段。完整文档使用 `document` 根标签，片段使用 `fragment`；`include` 只能引用同一集合中的 XML 片段。集合发布成功后不可覆盖同一版本，运行中的请求始终使用一个完整版本快照。

下面两份 XML 可以作为同一模板集合发布。文档代码为 `document-template`，片段代码为 `document-items`。

```xml
<document xmlns="https://leyland.github.io/letool/print/v1"
          context-version="1"
          title="清单"
          author="Letool"
          language="zh-CN"
          outputs="pdf">
    <styles>
        <paragraph-style name="body" alignment="left" whitespace="collapse"/>
        <table-style name="items" width="100%" layout="fixed"
                     column-widths="60%,40%"/>
    </styles>
    <page size="A4" orientation="portrait" margin="18mm">
        <page-header><paragraph>文档清单</paragraph></page-header>
        <page-body>
            <table-of-contents title="目录" min-level="1" max-level="2"/>
            <heading id="document-title" level="1">清单</heading>
            <paragraph id="document-summary" style="body">
                编号：<field path="document.no"/>，名称：<field path="document.name"/>
            </paragraph>
            <if path="document.confirmed" operator="truthy">
                <then><paragraph>状态：已确认</paragraph></then>
                <else><paragraph>状态：待确认</paragraph></else>
            </if>
            <include template="document-items">
                <with name="items" path="document.items"/>
            </include>
            <paragraph>
                合计：<field path="document.total" formatter="number">
                    <format-option name="pattern" value="#,##0.00"/>
                    <format-option name="locale" value="zh-CN"/>
                </field>
            </paragraph>
            <paragraph><link target="document-title">返回标题</link></paragraph>
            <annotation type="text-note" target="document-summary" author="审核人">
                请核对清单信息
            </annotation>
        </page-body>
        <page-footer><paragraph>第 <page-number/> 页</paragraph></page-footer>
    </page>
</document>
```

```xml
<fragment xmlns="https://leyland.github.io/letool/print/v1" parameters="items">
    <table id="document-items" style="items">
        <header>
            <row>
                <cell><paragraph>名称</paragraph></cell>
                <cell><paragraph>金额</paragraph></cell>
            </row>
        </header>
        <body>
            <for-each items="$items" var="item">
                <row>
                    <cell><paragraph><field path="$item.name"/></paragraph></cell>
                    <cell><paragraph><field path="$item.amount" formatter="number"/></paragraph></cell>
                </row>
            </for-each>
        </body>
    </table>
</fragment>
```

对应上下文至少包含：

```json
{
  "document": {
    "no": "DOC-2026-001",
    "name": "示例清单",
    "confirmed": true,
    "total": 1280.50,
    "items": [
      {"name": "项目 A", "amount": 800},
      {"name": "项目 B", "amount": 480.50}
    ]
  },
  "review": {
    "reason": "金额需要复核"
  }
}
```

这份示例中的多页面序列、页眉页脚、逻辑页码、命名样式和文字流控制都可由当前内置 PDF 输出。图片等尚未开放的语义会在读取业务数据前报告能力不足，不会生成缺失内容的文档。

## 标签速查

| 标签 | 位置与用途 |
|---|---|
| `document` | 完整文档根，声明上下文版本、可选元数据和输出白名单 |
| `styles` | 声明文本、段落、表格和单元格命名样式 |
| `fragment` | 声明参数的可复用块级片段根，不单独打印 |
| `page` | 页面序列入口，支持布局、四边边距和逻辑页码 |
| `page-header/page-body/page-footer` | 有序页面区域，正文必须且只能出现一次 |
| `section` | 块级章节；动态子节点全部为空时自动剪枝 |
| `heading` | 1 至 6 级标题，可作为目录和链接目标 |
| `paragraph` | 普通段落，可混排文本、字段、书签和内部链接 |
| `text` | 显式文本节点，适合需要清晰区分空白的位置 |
| `field` | 从根数据或 `$循环变量` 读取标量并输出文本 |
| `if/then/else` | 显式真分支和可选假分支 |
| `for-each` | 遍历 JSON 数组，循环变量使用 `$name` 读取 |
| `table/header/body/row/cell` | 严格网格表格，动态域必须生成完整行 |
| `include/with` | 引入同一模板集合中的片段并显式传参 |
| `table-of-contents` | `page-body` 直接子节点，每份文档最多一个 |
| `bookmark`、`link` | 声明稳定目标和文档内跳转 |
| `annotation` | PDF 文本便签或自由文本框批注 |
| `image` | 只生成受控逻辑资源描述，当前内置 PDF 尚不显示图片 |
| `line-break` | 标题或段落内的显式换行 |
| `page-number/page-count` | 页眉、正文或页脚中的逻辑页码占位 |
| `page-break` | 显式物理分页 |

未知标签、属性、父子关系或重复 ID 都会在编译或绑定阶段失败。模板不能提供 Java 类、Bean 名、实现类、HTML、CSS、脚本、文件路径或网络地址。

## 页面与命名样式

一份文档可以按顺序声明多个 `page`。每个页面允许可选 `page-header`、必需 `page-body` 和可选 `page-footer`，三者顺序固定。`margin` 设置统一边距，也可以用 `margin-top/right/bottom/left` 覆盖单边。`numbering` 支持 `continue`、`restart` 和 `excluded`；重启页码时用 `start-page-number` 指定正整数，被排除页面不能放置 `page-number`。

节点只能通过 `style` 引用同类型命名样式，不支持局部样式覆盖。段落样式的 `whitespace` 支持 `collapse`、`preserve-line-breaks`、`preserve-all`，`wrap` 支持 `normal`、`break-long-words`、`no-wrap`。模板源码中的换行是否进入排版由段落样式解释；需要不受缩进影响的确定换行时使用 `<line-break/>`。

`document.outputs` 是可选的逗号分隔输出白名单，例如 `outputs="pdf"`。省略表示模板不限制格式；声明后，解析服务会在绑定前拒绝未列出的输出。

## 数据路径、循环和条件

根路径使用点分段，例如 `document.name`。循环变量和片段参数必须带 `$`：

```xml
<for-each items="groups" var="group">
    <heading level="2"><field path="$group.name"/></heading>
    <for-each items="$group.items" var="item">
        <paragraph><field path="$item"/></paragraph>
    </for-each>
</for-each>
```

变量采用词法作用域，不能与外层变量重名。循环后代不能声明稳定 ID，因为展开后会产生重复目标。字段缺失与对标量继续遍历是两类错误；框架不会把错误路径悄悄转换为空字符串。显式 JSON `null` 的字段输出空文本。

结构化条件不执行脚本：

```xml
<if path="document.total" operator="gte" value="1000.00" value-type="number">
    <then><paragraph>达到复核金额</paragraph></then>
    <else><paragraph>无需复核</paragraph></else>
</if>
```

`then` 必须存在，`else` 可以省略。`value-type` 支持 `string`、`number`、`boolean` 和 `null`。操作符还包括 `exists/not-exists`、`is-null/not-null` 与 `empty/not-empty`；框架区分路径缺失、显式 `null`、空字符串和空容器，类型不匹配不会做模糊转换。

## 字段格式化

内置格式化器包括：

- `number`：`pattern`、`locale`、`rounding-mode`；
- `date`：`pattern`、`input-pattern`、`locale`、`zone-id`；
- `datetime`：`pattern`、`input-pattern`、`locale`、`zone-id`；
- `boolean`：`true-text`、`false-text`；
- `join`：`separator`，用于拼接标量数组并跳过 `null`。

未声明 `locale` 时使用 `Locale.ROOT`，未声明 `zone-id` 时使用 UTC。日期字符串默认按 ISO 语义读取，数字时间值按 epoch millis 解释。选项名称不能重复，名称和值都必须非空。

## include 与目录

`include` 在发布时解析，缺失目标、引用文档而非片段、参数不完整、引用环、深度或展开节点超限都会拒绝整次发布。片段在根标签通过 `parameters` 声明参数，调用方用 `with name/path` 逐项传入；参数路径可以读取调用方循环变量，但片段不会隐式捕获调用点作用域。

`table-of-contents` 必须是 `page-body` 的直接子节点，不能放进章节、循环、条件、表格或片段。它只收录声明位置之后、层级范围内的标题。内置 PDF 最多进行五轮页码收敛；无法收敛时返回安全错误，不输出页码不可信的目录。

## 链接与批注

`link.target` 必须指向文档中存在的稳定 ID。`bookmark` 适合在段落内建立显式目标；带 ID 的标题和段落也可以作为目标。

文本便签：

```xml
<paragraph id="summary">需要复核的正文</paragraph>
<annotation type="text-note" target="summary" author="审核人">
    请复核 <field path="review.reason"/>
</annotation>
```

自由文本框：

```xml
<annotation type="free-text" target="summary" placement="bottom-left"
            width="50mm" height="20mm" offset-x="1.5mm" offset-y="-2mm">
    页面上可见的说明
</annotation>
```

方位支持 `top-left`、`top-right`、`bottom-left`、`bottom-right`。自由文本框需要宿主配置可嵌入字体，中文外观优先使用最终回退字体。模板不能配置批注脚本、动作、附件、颜色或 PDF 原始坐标。

## 图片资源边界

静态逻辑 ID：

```xml
<image resource-id="company.logo" alt="公司标志" width="30mm" height="12mm"/>
```

动态逻辑 ID：

```xml
<image resource-path="branding.logoResourceId" alt="公司标志" width="30mm" height="12mm"/>
```

模板只产生 `ImageNode`，不会把逻辑 ID 当作 URL、文件或 classpath 路径读取。当前内置 PDF 明确不支持 `ImageNode`，遇到图片会报告输出能力不支持，而不是静默丢图。资源显示属于后续扩展能力，接入时仍应由宿主把逻辑 ID 映射到受控资源。

## 可选受限 SpEL

SpEL 不随主 Starter 自动启用。项目需要同时引入 `letool-starter-print-expression-spel` 并配置：

```yaml
letool:
  print:
    spel:
      enabled: true
```

模板随后可以使用：

```xml
<if expression-language="spel"
    test="document.confirmed == true and document.total >= 1000">
    <then><paragraph>已确认且达到复核金额</paragraph></then>
</if>
```

这里只开放 JSON 属性、数组下标、字面量、比较、逻辑运算和括号。Bean、类型、构造器、方法、函数、反射元数据、写入、算术、集合投影等语法均被拒绝。表达式最终结果必须是 `Boolean`，不会把字符串或数字转换成真值。

## 治理上限

模板作者应在数据适配阶段控制集合规模，不要依赖达到上限后再失败。当前固定上限包括：

| 范围 | 上限 |
|---|---:|
| 单模板原始节点 | 20,000 |
| 单模板结构深度 | 64 |
| 模板集合原始节点 | 200,000 |
| include 展开节点 | 100,000 |
| include 深度 | 32 |
| 单段模板文本 | 1,000,000 字符 |
| 数据路径 | 256 字符、32 段 |
| 动态嵌套深度 | 16 |
| 单次循环元素 | 10,000 |
| 单次绑定累计动态操作 | 100,000 |
| 单次绑定生成节点 | 100,000 |
| 单次绑定生成文本 | 2,000,000 字符 |
| 表达式正文 | 4,096 字符 |
| 模板集合 | 10,000 份、总内容 256 MiB |
| PDF 批注 | 1,000 条 |
| PDF 分段排版单元 | 1,000 个 |

PDF 最大页数和产物字节数由宿主配置，默认分别为 2,500 页和 100 MiB。

## 发布前检查

模板编译结果通过 `inspection()` 提供不可变静态契约，包括全部可能分支的数据路径、include 参数、格式化器、表达式语言、节点类型、文档特性和输出白名单。框架在业务绑定前先核对目标渲染器能力，绑定后再检查动态生成的真实文档；检查结果不会携带 XML 正文、表达式正文、比较值或业务数据。

## 安全错误

模板发布、绑定和渲染错误使用稳定打印错误码，并只保留模板代码、安全行列、标签或属性等定位信息。模板正文、业务字段值、表达式原文、文件路径、字体路径和第三方异常消息不会作为用户可见参数。

排查时先根据错误码和安全位置检查模板结构，再查看服务端原因链。不要把业务数据或完整模板拼进自定义异常；自定义格式化器、表达式和标签的实现消息也会被框架转换为安全错误。
