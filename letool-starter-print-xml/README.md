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

该模块只依赖 `letool-starter-print`，不依赖 Spring、数据库、EDC 业务模型、OpenHTMLToPDF、PDFBox、docx4j、JasperReports 或表达式引擎。

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
- 标签、属性、父子关系、ID、标题层级、节点数量、深度和文本长度校验。

## 数据路径与空值

- 根路径使用 `policy.holder.name`，循环变量路径使用 `$coverage.name`；
- 路径只允许对象字段，不支持下标、通配符、方法、类名或任意表达式；
- 缺失路径绑定失败，显式 JSON `null` 作为已存在空值；
- `field` 遇到显式空值输出空文本，对象和数组不能直接输出；
- `for-each` 遇到显式空值按空数组处理，非数组值绑定失败；
- 循环变量只在循环体内有效，内层可以读取外层变量但禁止重名；
- 循环后代暂时禁止声明静态 `id`，动态 ID、书签和链接在后续阶段统一设计。
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

`value-type` 支持 `string/number/boolean/null`，默认 `string`。数字使用十进制精确比较。表格、图片、导航、include、标签 SPI 和格式化器属于阶段 2C；可选受限 SpEL 属于独立阶段 2D 模块。

## 安全边界

- 只接受固定 DSL 命名空间和严格 UTF-8。
- 在解析前拒绝 DOCTYPE、ENTITY、XInclude、外部 Schema 和可执行表达式标记。
- StAX 显式关闭 DTD、外部实体和实体替换，并使用拒绝型外部资源解析器。
- 未知标签、属性和错误结构在编译阶段失败。
- XML 不能调用 Java、反射、Spring Bean、SQL、脚本、文件系统或网络。
- 用户可见编译错误只包含模板代码、标签及安全行列位置，不回显模板正文或外部资源地址。
- 动态绑定限制路径长度、路径段数、循环嵌套、单循环元素、累计动态操作、生成节点和生成文本总量。

宿主应用仍负责权限、数据查询、脱敏、字典翻译和业务计算；本模块不会为了 EDC 或其他首个使用方加入领域定制逻辑。
