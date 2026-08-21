# letool-starter-print

`letool-starter-print` 是 Letool 动态报表框架的纯核心模块，提供同步请求、只读上下文、通用文档模型、模板管线路由、输出渲染 SPI 和稳定错误契约。受控 XML 编译位于独立的 `letool-starter-print-xml` 模块，当前内置输出由 `letool-starter-print-pdf` 提供，其他格式可按实际需求接入扩展点。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-print</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 核心调用方式

宿主应用先完成查询、权限、脱敏、字典翻译和业务计算，再把规范化对象树包装为 `PrintContext`。核心不会访问 Entity、Mapper、数据库、HTTP 接口或 Spring Bean。

```java
PrintContext context = PrintContext.of(1, objectNode);
PrintTemplate template = new PrintTemplate(
        "contract",
        TemplateFormat.LETOOL_XML,
        1,
        12,
        1,
        templateBytes);

PrintRequest request = new PrintRequest(
        template,
        context,
        OutputFormat.PDF,
        Locale.SIMPLIFIED_CHINESE,
        ZoneId.of("Asia/Shanghai"),
        RenderOptions.defaults());

try (OutputStream output = Files.newOutputStream(target)) {
    PrintResult result = printEngine.renderTo(request, output);
    log.info("打印完成：bytes={}, sha256={}", result.contentLength(), result.sha256());
}
```

调用方拥有并负责关闭输出流，框架只会刷新，不会替业务代码关闭。小文档需要直接取得字节数组时，仍可调用 `printEngine.render(request)` 返回 `PrintArtifact`。

## 通用文档模型

`DocumentModel` 由文档元数据、强类型 `StyleSheet` 和一个或多个 `PageSequence` 组成。每个页面序列拥有自己的物理页面布局、可重复页眉页脚、逻辑页码规则和正文，适合合同封面、正文、附录使用不同页面规则的场景：

```java
StyleSheet styles = StyleSheet.builder()
        .text("body-text", TextStyle.builder()
                .fontSize(DocumentLength.points(10.5))
                .build())
        .paragraph("body", ParagraphStyle.builder()
                .textStyleName("body-text")
                .alignment(TextAlignment.JUSTIFY)
                .build())
        .build();

PageSequence sequence = new PageSequence(
        PageLayout.a4Portrait(),
        new PageRegion(List.of(headerParagraph)),
        new PageRegion(List.of(footerParagraph)),
        PageNumbering.countedFrom(1),
        List.of(new ParagraphNode("content", "body", List.of(new TextNode("正文")))));

DocumentModel document = new DocumentModel(metadata, styles, List.of(sequence));
```

只有一个正文序列且不需要命名样式时，可使用 `DocumentModel.singleSequence(...)`。模型在构造时完成结构、ID、链接、目录、样式引用、表格网格和页码语义校验，不再要求调用方额外执行 `validate()`。

命名样式分为文本、段落、表格和单元格四类，节点只能引用对应类型。框架不接受 CSS、未知样式键或样式继承；空字符串表示使用稳定的框架默认样式。页眉页脚允许段落、表格、图片和章节，但不能声明逻辑 ID，也不能包含标题、目录、批注或显式分页。

阶段 1 可以使用自定义假管线验证完整门面：

```java
PrintPipeline pipeline = new PrintPipeline() {
    public TemplateFormat templateFormat() {
        return TemplateFormat.LETOOL_XML;
    }

    public Set<OutputFormat> supportedOutputs() {
        return Set.of(OutputFormat.PDF);
    }

    public PrintResult render(PrintRequest request, PrintOutput output) {
        output.write(generatedBytes);
        return output.complete(OutputFormat.PDF, Map.of());
    }
};

PrintEngine printEngine = new DefaultPrintEngine(
        new PrintPipelineRegistry(List.of(pipeline)));
```

## 扩展层级

```text
PrintEngine
├─ TemplateFormat.LETOOL_XML
│  └─ XmlPrintPipeline → DocumentModel → DocumentRenderer
│     └─ PDF renderer
└─ 自定义 TemplateFormat
   └─ 独立模型对应的自定义 PrintPipeline
```

- 能稳定消费 `DocumentModel` 的新格式实现 `DocumentRenderer`。
- PDF 实现统一通过 `PdfRenderer` 扩展；默认实现是 `OpenHtmlPdfRenderer`，宿主替换后若渲染失败，框架不会退回默认实现再次输出。
- 具有独立报表模型的格式实现顶层 `PrintPipeline`，例如后续 JasperReports 包装或与流式文档语义不同的表格型导出。
- `OutputFormat` 是开放值对象，核心预置 PDF，但不会把外部扩展永久限制为这一种格式。
- JasperReports 借鉴其模板编译、数据填充、导出、缓存、虚拟化和 Governor 思想，但不会反向塑形 Letool XML 与 `DocumentModel`。
- 输出能力除节点类型外还要声明 `DocumentFeature`；页面序列、页眉页脚、逻辑页码、命名样式和文字流控制等语义未实现时会在渲染前明确失败。

## 不可变性和资源边界

- `PrintTemplate`、`PrintContext`、内存 `PrintArtifact` 和文档树在输入输出边界进行防御性复制。
- `PrintContext` 版本必须与模板声明的上下文版本一致。
- XML 管线会先用编译 inspection 检查目标输出能力，再读取业务数据；绑定完成后还会对动态生成的文档执行一次真实能力检查。
- `DefaultPrintEngine` 写入时限制产物大小，完成后核对结果确实来自当前输出。
- `PrintOutput` 持续计算 64 位内容长度和 SHA-256，不保存完整正文；失败或完成后不能继续写入。
- 内存入口复用同一条流式主链路，仅适合明确可放入堆内的小文档。

## Letool 模块复用

- 错误模型复用 `letool-starter-exception`。
- 核心上下文直接使用 Jackson `JsonNode`；当前 `letool-starter-tool` 的 JSON API 基于 Fastjson2，且依赖闭包包含 Spring/SpEL，不适合为了表面复用引入纯核心。
- 后续模板流、资源和产物存储会优先评估 `IoUtil` 与 `letool-starter-file`，但只在依赖方向合适的 XML、输出或交付模块中复用。
- 不在打印模块复制已有 JSON、IO、文件、缓存、任务或监控工具。

## 安全与异常

- 模板和上下文均不得执行 Java、SpEL、脚本、SQL 或反射表达式。
- 管线实现不得修改请求数据，也不得绕过请求的输出限制。
- 已分类 Letool `BaseException` 原样传播；未知运行时故障包装为 `PrintPipelineException` 并保留 cause。
- 用户可见异常不包含模板正文、业务数据、绝对路径、密钥或第三方原始消息。

## 后续路线

后续按阶段完善 Spring Boot 业务适配、模板治理、JasperReports 并行管线和可选异步交付。新增输出格式应先有明确场景与验收标准，并继续保持同步核心不依赖 Spring、数据库和具体文档库。
