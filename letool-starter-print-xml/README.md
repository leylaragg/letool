# letool-starter-print-xml

`letool-starter-print-xml` 是 Letool 动态打印框架的受控 XML DSL 模块。阶段 2A 提供无 DOM 暴露的安全编译入口，并把静态基础标签绑定为核心 `DocumentModel`。

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

## 阶段 2A 标签

```xml
<document xmlns="https://leyland.github.io/letool/print/v1"
          context-version="1"
          title="合同"
          author="Letool"
          language="zh-CN">
    <page size="A4" orientation="portrait" margin="20mm">
        <section id="summary">
            <heading id="title" level="1">合同摘要</heading>
            <paragraph id="intro">静态正文</paragraph>
            <page-break/>
        </section>
    </page>
</document>
```

当前支持：

- `document`：上下文版本以及可选标题、作者、语言；
- `page`：A4/LETTER、portrait/landscape 和统一毫米边距；
- `section`、`heading`、`paragraph`、`text` 和 `page-break`；
- 标签、属性、父子关系、ID、标题层级、节点数量、深度和文本长度校验。

`field`、`if`、`for-each`、表格、图片、导航、include、标签 SPI 和格式化器属于后续 2B/2C，不会用未受控的通用 XML 能力提前替代。

## 安全边界

- 只接受固定 DSL 命名空间和严格 UTF-8。
- 在解析前拒绝 DOCTYPE、ENTITY、XInclude、外部 Schema 和可执行表达式标记。
- StAX 显式关闭 DTD、外部实体和实体替换，并使用拒绝型外部资源解析器。
- 未知标签、属性和错误结构在编译阶段失败。
- XML 不能调用 Java、反射、Spring Bean、SQL、脚本、文件系统或网络。
- 用户可见编译错误只包含模板代码、标签及安全行列位置，不回显模板正文或外部资源地址。

宿主应用仍负责权限、数据查询、脱敏、字典翻译和业务计算；本模块不会为了 EDC 或其他首个使用方加入领域定制逻辑。
