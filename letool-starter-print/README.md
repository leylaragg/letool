# letool-starter-print

`letool-starter-print` 是 Letool 动态报表框架的纯核心模块，提供同步请求、只读上下文、通用文档模型、模板管线路由、输出渲染 SPI 和稳定错误契约。受控 XML 编译位于独立的 `letool-starter-print-xml` 模块，当前内置输出由 `letool-starter-print-pdf` 提供，其他格式可按实际需求接入扩展点。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
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

PrintArtifact artifact = printEngine.render(request);
```

阶段 1 可以使用自定义假管线验证完整门面：

```java
PrintPipeline pipeline = new PrintPipeline() {
    public TemplateFormat templateFormat() {
        return TemplateFormat.LETOOL_XML;
    }

    public Set<OutputFormat> supportedOutputs() {
        return Set.of(OutputFormat.PDF);
    }

    public PrintArtifact render(PrintRequest request) {
        return PrintArtifact.of(OutputFormat.PDF, generatedBytes, Map.of());
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
- 具有独立报表模型的格式实现顶层 `PrintPipeline`，例如后续 JasperReports 包装或与流式文档语义不同的表格型导出。
- `OutputFormat` 是开放值对象，核心预置 PDF，但不会把外部扩展永久限制为这一种格式。
- JasperReports 借鉴其模板编译、数据填充、导出、缓存、虚拟化和 Governor 思想，但不会反向塑形 Letool XML 与 `DocumentModel`。

## 不可变性和资源边界

- `PrintTemplate`、`PrintContext`、`PrintArtifact` 和文档树在输入输出边界进行防御性复制。
- `PrintContext` 版本必须与模板声明的上下文版本一致。
- `DefaultPrintEngine` 在管线执行前检查输出能力，执行后检查产物格式和大小。
- 当前产物为有界内存字节数组。大文档流式输出、临时文件和虚拟化策略将在 PDF 技术切片中确定。
- API 当前没有接收调用方流，因此不存在关闭调用方流的行为；以后增加流 API 时会显式说明所有权。

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
