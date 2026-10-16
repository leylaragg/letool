package io.github.leylaragg.letool.print.pdf;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 最终合并后按真实源字体扩展，共享完整字体流并保留 W 与 ToUnicode。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfFullFontWriter {
    /** 宿主注册的真实源字体，用于校验最终 PDF 的字体身份。 */
    private final PdfFontCatalog catalog;

    /** @param catalog 本次渲染使用的字体目录 */
    PdfFullFontWriter(PdfFontCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 为最终文档中的同名字体共享完整字体流，保留各资源的字符宽度与 Unicode 映射。
     *
     * @param document 已合并的 PDF；调用方负责保存和关闭
     * @throws IOException 字体源冲突、无法匹配或字体读写失败
     */
    void write(PDDocument document) throws IOException {
        // 同一 PostScript 身份必须对应同一真实源文件，防止合并后错误替换字体流。
        Map<String, byte[]> sources = new LinkedHashMap<>();
        for (PdfFont font : catalog.fonts()) {
            byte[] raw = PdfFontSource.read(font);
            String name = PdfFontSource.postScriptName(raw);
            byte[] previous = sources.putIfAbsent(name, raw);
            if (previous != null && !Arrays.equals(previous, raw)) {
                throw new IOException("字体源身份冲突");
            }
        }

        // 完整字体按身份只加载一次，资源字典按对象身份去重以处理共享或递归表单。
        Map<String, PDType0Font> complete = new LinkedHashMap<>();
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PDPage page : document.getPages()) {
            expand(document, page.getResources(), sources, complete, visited);
            // FreeText 的可见文字属于批注外观资源，也必须满足文档的完整字体契约。
            for (PDAnnotation annotation : page.getAnnotations()) {
                PDAppearanceStream appearance = annotation.getNormalAppearanceStream();
                if (appearance != null) {
                    expand(document, appearance.getResources(), sources, complete, visited);
                }
            }
        }
    }

    /** 按资源字典身份去重遍历页面与嵌套表单，避免共享资源被重复扩展。 */
    private void expand(PDDocument document, PDResources resources, Map<String, byte[]> sources,
        Map<String, PDType0Font> complete, Set<COSDictionary> visited) throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) {
            return;
        }
        for (COSName key : resources.getFontNames()) {
            PDFont candidate = resources.getFont(key);
            if (!(candidate instanceof PDType0Font used)) {
                throw new IOException("完整嵌入需要可定位的 Type0 字体");
            }
            String sourceName = used.getName().replaceFirst("^[A-Z]{6}\\+", "");
            byte[] raw = sources.get(sourceName);
            if (raw == null) {
                throw new IOException("PDF 字体无法定位到宿主源");
            }
            PDType0Font full = complete.get(sourceName);
            if (full == null) {
                full = PDType0Font.load(document, new ByteArrayInputStream(raw), false);
                complete.put(sourceName, full);
            }
            // 只替换源字体和 GID 映射，保留原资源的 W 宽度表和 ToUnicode 字符映射。
            used.getCOSObject().setName(COSName.BASE_FONT, sourceName);
            used.getDescendantFont().getCOSObject().setName(COSName.BASE_FONT, sourceName);
            used.getDescendantFont().getCOSObject().setItem(COSName.CID_TO_GID_MAP, COSName.IDENTITY);
            used.getFontDescriptor().setFontName(sourceName);
            used.getFontDescriptor().setFontFile2(full.getFontDescriptor().getFontFile2());
            used.getFontDescriptor().getCOSObject().removeItem(COSName.CID_SET);
        }

        // 字体也可能被嵌套在表单资源中，必须沿同一去重集合继续处理。
        for (COSName key : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(key);
            if (object instanceof PDFormXObject form) {
                expand(document, form.getResources(), sources, complete, visited);
            }
        }
    }
}
