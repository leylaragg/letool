# letool-starter-print-docx

`letool-starter-print-docx` 使用 docx4j 将打印核心的不可变 `DocumentModel` 写成可编辑 DOCX。它和 PDF 渲染器共享 XML、数据绑定与文档模型，不承诺分页、断行或像素位置一致；确实需要格式专属排版时，宿主可以显式选择独立模板，并继续复用公共片段。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-print-docx</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块生产依赖方向为 `print-docx -> print core / docx4j`，不会反向依赖 PDF、XML、Spring 或业务模块。docx4j 使用 Apache-2.0 许可证，应用发布时仍应结合自身交付方式完成许可证审查。

## 基础用法

```java
DocxRendererOptions docxOptions = new DocxRendererOptions(
        DocxCompatibilityMode.COMPATIBLE,
        "Arial",
        "SimSun",
        21);
DocxDocumentRenderer renderer = new DocxDocumentRenderer(docxOptions);

RenderedDocument rendered = renderer.render(document, RenderOptions.defaults());
byte[] docx = rendered.content();
```

`DocxDocumentRenderer` 只保存不可变配置。每次 `render` 都创建独立的 OOXML 包、书签映射、降级状态和受限输出缓冲，因此同一个实例可以并发渲染不同文档。

## 当前支持的文档语义

- 页面尺寸、横纵方向和四边边距；
- 一至六级标题、段落、文本换行、逻辑章节和显式分页；
- 严格网格表格、跨行跨列、嵌套块内容和重复表头；
- 安全书签名、文档内链接和可更新的 Word TOC 域；
- 可选标题、作者和语言元数据；
- 图片占位和批注尾注两种兼容表达。

目录包含声明位置之后、层级范围内的标题缓存。Word 或 WPS 打开后需要更新域才能得到最终目录页码；渲染结果的 `fieldUpdateRequired` 元数据会明确标记这一点。DOCX 的最终页数受字体与编辑器排版环境影响，因此本模块不执行 `RenderOptions.maxPages`，也不伪造 `pageCount`；`maxOutputBytes` 仍然严格生效。

## 兼容与严格模式

默认的 `COMPATIBLE` 模式优先保证文档可生成：图片在原位置写成受控单格占位区，只展示替代文字且不会读取 `resourceId`；定位批注写成锚定目标的 Word 尾注，保留作者和正文。

`STRICT` 模式不接受这两种非等价表达。文档含有 `ImageNode` 或 `AnnotationNode` 时，会在创建 OOXML 包之前失败。无论选择哪种模式，非法模型、缺失链接、安全违规和容量越界都不会被降级处理。

渲染结果固定提供以下统计元数据：

- `compatibilityMode`：实际使用的兼容模式；
- `degradedNodeCount`：发生替代表达的节点数；
- `degradedNodeTypes`：按名称排序的降级类型；
- `fieldUpdateRequired`：是否需要编辑器更新域；
- `contentLength`：最终 DOCX 字节数。

这些元数据不包含正文、作者、逻辑 ID、资源 ID、字体名称或文件路径。

## 安全与资源边界

DOCX 从空包开始构造，不读取外部 Word 模板，也不接受模板侧 OOXML、关系类型、URI 或实现类配置。保存前会检查全部部件和关系，拒绝外部关系、宏、ActiveX、OLE、附件、嵌入对象和 `altChunk`。输出使用打印核心的分段缓冲，在分配和复制前检查容量，第三方异常原文只保留在可信原因链中。

当前阶段不解析或嵌入图片、SVG 和字体文件，也不提供页眉页脚、页码域、物理分节、原生 Word 批注、浮动文本框、列表编号或旧 `.doc`。这些能力会按独立阶段扩展，不改变现有共享模板和兼容模式的语义。
