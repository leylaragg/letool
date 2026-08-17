# letool-starter-print-spring-boot

`letool-starter-print-spring-boot` 是动态打印框架的 Spring Boot 主入口。业务项目引入一个 Maven 依赖后，即可获得模板仓库、XML 编译与缓存、PDF 渲染、统一打印引擎和类型化业务门面。

Starter 只编排通用打印能力，不负责业务查询、数据权限、模板来源或字体许可证。宿主通过 `PrintDefinition` 把自己的请求转换为只读 `PrintContext`，模板再使用同一份 XML 输出 PDF。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-print-spring-boot</artifactId>
    <version>${letool.version}</version>
</dependency>
```

主 Starter 传递引入打印核心、模板仓库、XML DSL 和 PDF 渲染模块。受限 SpEL 是可选能力，需要时再显式增加：

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-print-expression-spel</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 配置

```yaml
letool:
  print:
    enabled: true
    renderer-profile-version: 1
    locale: zh-CN
    zone-id: Asia/Shanghai
    max-pages: 2500
    max-output-bytes: 104857600
    include-document-metadata: true
    template-set-cache-capacity: 64
    template-cache-capacity: 1024
    temporary-directory: ""
    spel:
      enabled: false
```

`renderer-profile-version` 参与编译缓存键。字体、样式或渲染配置发生不兼容变化时应提升版本。`temporary-directory` 为空时沿用 PDF 模块的受控临时目录策略。

引入 SpEL 模块本身不会启用表达式；只有依赖存在且 `letool.print.spel.enabled=true` 时，XML 才接受 `expression-language="spel"`。显式开启但缺少模块会在启动阶段失败，避免不同环境悄悄使用不同模板语义。

## 声明业务打印定义

```java
@Configuration
public class InvoicePrintConfiguration {

    @Bean
    PrintDefinition<Long> invoicePrintDefinition(InvoiceQueryService queryService) {
        return PrintDefinition.of(
                "invoice",
                "invoice-template",
                Long.class,
                invoiceId -> {
                    InvoicePrintData data = queryService.loadForPrint(invoiceId);
                    ObjectNode root = JsonNodeFactory.instance.objectNode()
                            .put("invoiceNo", data.invoiceNo())
                            .put("customerName", data.customerName());
                    return PrintContext.of(1, root);
                });
    }
}
```

定义编码供业务调用，模板编码指向模板集合里的 `DOCUMENT`。适配器由宿主实现，因此查询、鉴权和业务状态校验仍留在业务层；框架不会从 XML 反射调用 Spring Bean 或业务对象。

## 配置可信字体

英文模板可以不声明字体。需要中文或特定字形时，宿主应提供自己有权使用的字体资源：

```java
@Bean
PdfFont printFallbackFont(ResourceLoader resources) {
    Resource font = resources.getResource("classpath:fonts/NotoSansSC-Regular.ttf");
    return new PdfFont("Noto Sans SC", () -> {
        try {
            return font.getInputStream();
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开打印字体", exception);
        }
    }, true);
}
```

每次调用供应器都要返回新的输入流。XML 不接受本地字体路径，空字体配置也不保证中文可以正确显示。

## 发布模板

模板发布前会完成 XML 结构、引用和扩展能力校验；同一版本发布后不能覆盖。下面示例发布并激活版本 `1`：

```java
String xml = """
        <document xmlns="https://leyland.github.io/letool/print/v1"
                  context-version="1">
            <page>
                <heading>发票</heading>
                <paragraph>编号：<field path="invoiceNo"/></paragraph>
                <paragraph>客户：<field path="customerName"/></paragraph>
            </page>
        </document>
        """;

PrintTemplate template = new PrintTemplate(
        "invoice-template",
        TemplateFormat.LETOOL_XML,
        XmlDsl.VERSION,
        1,
        1,
        xml.getBytes(StandardCharsets.UTF_8));

templateSetPublisher.publishAndActivate(
        1,
        List.of(new TemplateDefinition(TemplateType.DOCUMENT, template)));
```

生产项目可以从数据库、配置中心或受控文件读取模板，再交给 `TemplateSetPublisher`。持久化仓库可通过实现并声明 `TemplateRepository` Bean 替换默认内存仓库。

## 生成 PDF

```java
PrintArtifact current = printService.render("invoice", invoiceId);
PrintArtifact historical = printService.render(1, "invoice", invoiceId);

byte[] pdf = current.content();
String sha256 = current.sha256();
```

不带版本的方法在请求开始时锁定当前模板集合快照；明确版本的方法用于重打历史文档。运行中的模板切换不会把两个版本混进同一份产物。

## 扩展与边界

- 自定义 `PrintValueFormatter`、`PrintConditionExpression`、`PrintTagHandler`、`DocumentRenderer` 或 `PrintPipeline` 可以声明为 Spring Bean，Starter 会按 Spring 顺序收集后冻结注册表。
- 自定义扩展编码或输出格式重复时，应用在启动阶段失败，不采用“最后一个覆盖前一个”的不确定语义。
- 宿主可以替换仓库、注册表、编译器、渲染器、管线、引擎或 `PrintService`；默认 Bean 会退让。
- `PrintService` 当前固定输出 PDF。其他输出格式通过核心 `PrintEngine` 和相应渲染器/管线扩展，不把业务判断写进通用框架。
- JasperReports 不是内置依赖。需要接入时应实现独立 `PrintPipeline` 适配模块，通过 `TemplateFormat` 扩展，不修改 XML 主链路。
