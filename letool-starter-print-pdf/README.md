# letool-starter-print-pdf

`letool-starter-print-pdf` 把打印核心的不可变 `DocumentModel` 渲染为真实 PDF。模块内部先生成框架控制的 XHTML，再由 OpenHTMLToPDF 和 PDFBox 完成排版与写出；模板和业务数据不能直接提供 HTML、CSS、文件路径或网络地址。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
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
        () -> ReportApplication.class.getResourceAsStream("/fonts/NotoSansCJKsc-Regular.otf"),
        true);

PdfDocumentRenderer renderer = new PdfDocumentRenderer(List.of(font));
RenderedDocument rendered = renderer.render(document, RenderOptions.defaults());
byte[] pdf = rendered.content();
```

字体供应器必须在每次调用时打开新的输入流。`PdfFont` 会拒绝不安全的字体族名称和空流；底层字体解析错误只保留在异常原因链中，不会把字体路径或第三方消息写入用户可见异常。

## 当前支持的文档语义

- A4、Letter 或自定义物理页面尺寸，横向或纵向，以及四边毫米级边距；
- 章节、一至六级标题、段落和显式分页；
- 严格网格表格、跨行跨列和跨页重复表头；
- 文本、书签、PDF 大纲和文档内链接；
- 便签批注和带嵌入字体外观的自由文本框批注；
- 可选标题、作者和语言元数据；
- 宿主字体注册、字体嵌入及最终回退字体；
- 最大页数、最大输出字节数和安全渲染异常。

`PdfDocumentRenderer` 只保存不可变字体配置。每次 `render` 都创建独立的 XHTML、输出缓冲区和第三方排版器，因此同一实例可以并发处理不可变文档。

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

输出采用分段缓冲，在扩容和复制前检查 `RenderOptions.maxOutputBytes`。布局完成后、PDF 写出前检查 `maxPages`。第三方异常原文、XHTML、业务正文和字体路径不会成为用户可见消息参数。

## 当前限制

本模块当前只完成单个 `DocumentModel` 的 PDF 垂直链路：

- `ImageNode` 和其他资源显示尚未开放；
- 多章节 PDF 合并和全局目录留在后续阶段；
- 不提供模板侧 HTML、CSS、脚本或任意 URI 扩展入口。

测试目录中的 `DroidSansFallback.ttf` 仅用于验证中文字体嵌入，不会进入模块主产物；其 Apache-2.0 NOTICE 与字体文件一同保留。
