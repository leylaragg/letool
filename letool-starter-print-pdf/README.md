# letool-starter-print-pdf

`letool-starter-print-pdf` 把打印核心的不可变 `DocumentModel` 渲染为真实 PDF。模块内部先生成框架控制的 XHTML，再由 OpenHTMLToPDF 和 PDFBox 完成排版与写出；模板和业务数据不能直接提供 HTML、CSS、文件路径或网络地址。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-print-pdf</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块依赖方向固定为 `print-pdf -> print core / OpenHTMLToPDF / PDFBox`，打印核心不会反向依赖 PDF 实现。OpenHTMLToPDF 使用 LGPL-2.1-or-later，PDFBox 使用 Apache-2.0；应用发布时应结合自身交付方式完成许可证审查。

## 基础用法

框架不捆绑生产字体。宿主应提供具有所需字形、允许嵌入 PDF 且许可证合规的字体源：

```java
PdfFont font = new PdfFont(
        "Noto Sans CJK SC",
        FontWeight.NORMAL,
        () -> ReportApplication.class.getResourceAsStream("/fonts/NotoSansCJKsc-Regular.otf"),
        true);

PdfRenderer renderer = new OpenHtmlPdfRenderer(PdfFontCatalog.of(List.of(font)));
RenderOptions options = RenderOptions.defaults();
try (OutputStream target = Files.newOutputStream(pdfPath)) {
    PrintOutput output = new PrintOutput(target, options.maxOutputBytes());
    PrintResult result = renderer.render(document, options, output);
}
```

分段排版会在宿主可信目录下创建随机请求子目录。默认根目录为
`${java.io.tmpdir}/letool/print-pdf`，也可以在 Java 配置中显式指定：

```java
PdfRenderer renderer = new OpenHtmlPdfRenderer(
        PdfFontCatalog.of(List.of(font)), Path.of("/trusted/runtime/letool/print-pdf"));
```

模板和业务数据不能指定临时路径。请求成功或失败后都会清理本次子目录；清理失败不会覆盖原始渲染异常。

同一字体族可以按 `FontWeight` 注册多个字体面，族名和字重组合不能重复，最终回退族也只能有一个。字体供应器必须在每次调用时打开新的输入流；底层字体解析错误只保留在异常原因链中，不会把字体路径或第三方消息写入用户可见异常。

## 当前支持的文档语义

- A4、Letter 或自定义物理页面尺寸，横向或纵向，以及四边毫米级边距；
- 多页面序列、每个序列独立的页面尺寸与页边距，以及可重复页眉页脚；
- 连续、重启或排除的逻辑页码，正文、页眉和页脚中的当前页及总页数；
- 文本、段落、表格和单元格命名样式，以及受控换行、空白和长词折行；
- 章节、一至六级标题、段落和显式分页；
- 根级全局目录、标题层级过滤、自动标题目标和最多五轮页码收敛；
- 严格网格表格、跨行跨列和跨页重复表头；
- 文本、书签、PDF 大纲和文档内链接；
- 便签批注和带嵌入字体外观的自由文本框批注；
- 可选标题、作者和语言元数据；
- 宿主字体注册、字体嵌入及最终回退字体；
- 最大页数、最大输出字节数、最大临时空间和安全渲染异常。

`OpenHtmlPdfRenderer` 只保存不可变字体目录和临时根目录配置。每次 `render` 都创建独立的 XHTML、请求工作区和第三方排版器，因此同一实例可以并发处理不可变文档。宿主若需要完全替换 PDF 链路，实现 `PdfRenderer` 即可；自定义实现失败时不会回退到默认实现，以免同一请求得到语义不同的产物。

## 全局目录

目录由 XML 根级 `table-of-contents` 声明，只收录声明位置之后的标题。标题可以省略 ID，PDF 渲染器会为本次输出建立稳定目标，不会修改通用文档模型：

```xml
<page>
    <page-body>
        <paragraph>封面</paragraph>
        <table-of-contents title="Contents" min-level="1" max-level="3"/>
        <heading level="1">第一章</heading>
    </page-body>
</page>
```

`title` 可省略，层级默认是 1 至 3，可在 1 至 6 之间调整。目录独占物理页面，条目带点引导、最终一基页码和文档内链接；框架最多重新排版五轮，仍无法收敛时安全失败。单个文档最多包含一个目录和 10,000 个目录条目。

## PDF 批注

XML 模板通过目标节点 ID 声明批注，正文只允许直接文本、`text` 和 `field`：

```xml
<paragraph id="summary">需要复核的正文</paragraph>
<annotation type="text-note" target="summary" author="审核人">
    请检查 <field path="review.reason"/>
</annotation>
<annotation type="free-text" target="summary" placement="bottom-left"
            width="50mm" height="20mm" offset-x="1.5mm" offset-y="-2mm">
    页面上可见的文本框内容
</annotation>
```

`text-note` 默认使用 6mm × 6mm，`free-text` 默认使用 50mm × 20mm；默认方位为 `top-right`。还可使用 `top-left`、`bottom-left` 和 `bottom-right`。方位选择目标首个可见片段的对应角点，正负 `offset-x`、`offset-y` 再移动最终矩形；矩形越出页面时渲染失败，不会被静默夹取。

自由文本框需要至少一个宿主字体生成中文外观，优先使用最终回退字体。批注正文不会进入 XHTML 页面正文；每个 PDF 最多包含 1,000 条批注，模板不能为批注配置脚本、动作、附件、颜色、资源或 PDF 原始坐标。

## 安全与资源边界

XHTML 标签、属性和 CSS 均由框架生成，文本和属性会分别转义。渲染器在 URI 解析前后都拒绝外部资源访问，不读取 HTTP、文件系统或 classpath 内容；字体流是宿主通过可信 Java 配置显式提供的唯一二进制输入。

PDF 会先写入本次请求独占的受控工作区，重新打开检查结构和页数后，再复制到调用方输出。`RenderOptions.maxOutputBytes` 限制每个最终 PDF，`maxTemporaryBytes` 限制单次请求所有活动临时文件的总量；两项都在实际写入前检查，单次最多建立 1,000 个排版单元。渲染失败时不会向调用方留下半份最终 PDF，第三方异常原文、XHTML、业务正文、字体路径和临时路径也不会成为用户可见消息参数。

调用方始终负责关闭最终输出流。框架只清理自己创建的请求工作区，不会删除宿主配置的临时根目录。

## 当前限制

本模块当前限制如下：

- `ImageNode` 和其他资源显示尚未开放；
- 不提供模板侧 HTML、CSS、脚本或任意 URI 扩展入口。

测试目录中的 `DroidSansFallback.ttf` 仅用于验证中文字体嵌入，不会进入模块主产物；其 Apache-2.0 NOTICE 与字体文件一同保留。
