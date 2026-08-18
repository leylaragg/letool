# 动态打印模板作者指南

本指南面向编写 Letool XML 的模板作者。模板只描述通用文档语义，业务查询、权限、字典翻译和计算应先由宿主应用完成，再通过 `PrintContext` 提供标准 JSON 数据。

## 模板集合

一次发布包含一个或多个完整文档和可复用片段。完整文档使用 `document` 根标签，片段使用 `fragment`；`include` 只能引用同一集合中的 XML 片段。集合发布成功后不可覆盖同一版本，运行中的请求始终使用一个完整版本快照。

下面两份 XML 可以作为同一模板集合发布。文档代码为 `invoice-template`，片段代码为 `invoice-items`。

```xml
<document xmlns="https://leyland.github.io/letool/print/v1"
          context-version="1"
          title="发票"
          author="Letool"
          language="zh-CN">
    <page size="A4" orientation="portrait" margin="18mm">
        <table-of-contents title="目录" min-level="1" max-level="2"/>
        <heading id="invoice-title" level="1">发票</heading>
        <paragraph id="invoice-summary">
            编号：<field path="invoice.no"/>，客户：<field path="invoice.customer"/>
        </paragraph>
        <if path="invoice.paid" operator="truthy">
            <paragraph>状态：已付款</paragraph>
        </if>
        <include template="invoice-items"/>
        <paragraph>
            合计：<field path="invoice.total" formatter="number">
                <format-option name="pattern" value="#,##0.00"/>
                <format-option name="locale" value="zh-CN"/>
            </field>
        </paragraph>
        <paragraph><link target="invoice-title">返回标题</link></paragraph>
        <annotation type="text-note" target="invoice-summary" author="审核人">
            请核对客户信息
        </annotation>
    </page>
</document>
```

```xml
<fragment xmlns="https://leyland.github.io/letool/print/v1">
    <table id="invoice-items">
        <header>
            <row>
                <cell><paragraph>名称</paragraph></cell>
                <cell><paragraph>金额</paragraph></cell>
            </row>
        </header>
        <body>
            <for-each items="invoice.items" var="item">
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
  "invoice": {
    "no": "INV-2026-001",
    "customer": "示例客户",
    "paid": true,
    "total": 1280.50,
    "items": [
      {"name": "项目 A", "amount": 800},
      {"name": "项目 B", "amount": 480.50}
    ]
  }
}
```

## 标签速查

| 标签 | 位置与用途 |
|---|---|
| `document` | 完整文档根，声明上下文版本和可选元数据 |
| `fragment` | 可复用块级片段根，不单独打印 |
| `page` | 文档页面布局入口，支持纸张、方向和毫米边距 |
| `section` | 块级章节；动态子节点全部为空时自动剪枝 |
| `heading` | 1 至 6 级标题，可作为目录和链接目标 |
| `paragraph` | 普通段落，可混排文本、字段、书签和内部链接 |
| `text` | 显式文本节点，适合需要清晰区分空白的位置 |
| `field` | 从根数据或 `$循环变量` 读取标量并输出文本 |
| `if` | 结构化条件或显式表达式条件 |
| `for-each` | 遍历 JSON 数组，循环变量使用 `$name` 读取 |
| `table/header/body/row/cell` | 严格网格表格，动态域必须生成完整行 |
| `include` | 引入同一模板集合中的片段 |
| `table-of-contents` | 根级全局目录，每份文档最多一个 |
| `bookmark`、`link` | 声明稳定目标和文档内跳转 |
| `annotation` | PDF 文本便签或自由文本框批注 |
| `image` | 只生成受控逻辑资源描述，当前内置 PDF 尚不显示图片 |
| `page-break` | 显式物理分页 |

未知标签、属性、父子关系或重复 ID 都会在编译或绑定阶段失败。模板不能提供 Java 类、Bean 名、实现类、HTML、CSS、脚本、文件路径或网络地址。

## 数据路径、循环和条件

根路径使用点分段，例如 `invoice.customer`。循环变量必须带 `$`：

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
<if path="invoice.total" operator="gte" value="1000.00" value-type="number">
    <paragraph>达到复核金额</paragraph>
</if>
```

`value-type` 支持 `string`、`number`、`boolean` 和 `null`。常用操作符包括 `truthy`、`falsy`、`eq`、`ne`、`gt`、`gte`、`lt`、`lte`；类型不匹配会安全失败，不做模糊转换。

## 字段格式化

内置格式化器包括：

- `number`：`pattern`、`locale`、`rounding-mode`；
- `date`：`pattern`、`input-pattern`、`locale`、`zone-id`；
- `datetime`：`pattern`、`input-pattern`、`locale`、`zone-id`。

未声明 `locale` 时使用 `Locale.ROOT`，未声明 `zone-id` 时使用 UTC。日期字符串默认按 ISO 语义读取，数字时间值按 epoch millis 解释。选项名称不能重复，名称和值都必须非空。

## include 与目录

`include` 在发布时解析，缺失目标、引用文档而非片段、引用环、深度或展开节点超限都会拒绝整次发布。片段可以读取根上下文并声明自己的循环变量，但不会捕获引用点的循环变量。

`table-of-contents` 必须是 `page` 的直接子节点，不能放进章节、循环、条件、表格或片段。它只收录声明位置之后、层级范围内的标题。内置 PDF 最多进行五轮页码收敛；无法收敛时返回安全错误，不输出页码不可信的目录。

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
<if expression-language="spel" test="invoice.paid == true and invoice.total >= 1000">
    <paragraph>已付款且达到复核金额</paragraph>
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

## 安全错误

模板发布、绑定和渲染错误使用稳定打印错误码，并只保留模板代码、安全行列、标签或属性等定位信息。模板正文、业务字段值、表达式原文、文件路径、字体路径和第三方异常消息不会作为用户可见参数。

排查时先根据错误码和安全位置检查模板结构，再查看服务端原因链。不要把业务数据或完整模板拼进自定义异常；自定义格式化器、表达式和标签的实现消息也会被框架转换为安全错误。
