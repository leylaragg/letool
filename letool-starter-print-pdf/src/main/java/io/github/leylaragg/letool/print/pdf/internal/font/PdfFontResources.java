package io.github.leylaragg.letool.print.pdf.internal.font;

import com.openhtmltopdf.extend.FSCacheEx;
import com.openhtmltopdf.extend.FSCacheValue;
import com.openhtmltopdf.extend.impl.FSDefaultCacheStore;
import io.github.leylaragg.letool.print.pdf.PdfFont;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 单次 PDF 渲染共用的字体源与度量缓存。
 *
 * <p>字体源在本次渲染期间应提供一致的内容，便于各轮布局复用名称和度量。PDF 字体对象归各自的文档管理。</p>
 *
 * @author leyland
 */
public final class PdfFontResources {

    private final long maxCacheBytes;
    private final Map<PdfFont, byte[]> sources = new IdentityHashMap<>();
    private final Map<PdfFont, String> postScriptNames = new IdentityHashMap<>();
    private final FSCacheEx<String, FSCacheValue> metricsCache = new FSDefaultCacheStore();
    private long retainedBytes;

    /**
     * 创建本次渲染的字体资源缓存。
     *
     * @param maxCacheBytes 可保留的字体源字节数，0 表示按需读取源字节
     * @throws IllegalArgumentException 字节预算为负数时抛出
     */
    public PdfFontResources(long maxCacheBytes) {
        if (maxCacheBytes < 0) {
            throw new IllegalArgumentException("字体二进制缓存预算不能为负数");
        }
        this.maxCacheBytes = maxCacheBytes;
    }

    /**
     * 按字体源对象读取规范化后的字节。返回数组仅供只读使用。
     *
     * @param font 当前请求字体目录中的源对象
     * @return 不与输入流共享生命周期的 TrueType 字节
     * @throws IOException 字体读取或规范化失败，失败结果不会进入缓存
     */
    public byte[] read(PdfFont font) throws IOException {
        byte[] cached = sources.get(font);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = PdfFontSource.read(font);
        // 预算只限制保留量。放不下的字体交给当前消费者，后续按需重新读取。
        if (maxCacheBytes > 0 && bytes.length <= maxCacheBytes - retainedBytes) {
            sources.put(font, bytes);
            retainedBytes += bytes.length;
        }
        return bytes;
    }

    /**
     * 打开独立的字体流，关闭该流不影响其他消费者。
     *
     * @param font 当前请求字体目录中的源对象
     * @return 从字体字节起点读取的输入流
     * @throws IOException 字体源读取或规范化失败时抛出
     */
    public InputStream openStream(PdfFont font) throws IOException {
        return new ByteArrayInputStream(read(font));
    }

    /**
     * 按需读取字体的 PostScript 名称，用于匹配排版与嵌入阶段的字体来源。
     *
     * @param font 当前请求字体目录中的源对象
     * @return 字体的 PostScript 名称
     * @throws IOException 字体读取或名称解析失败时抛出
     */
    public String postScriptName(PdfFont font) throws IOException {
        String cached = postScriptNames.get(font);
        return cached == null ? postScriptName(font, read(font)) : cached;
    }

    /**
     * 使用已读取的源字节解析 PostScript 名称，避免因字节未缓存而再次打开字体源。
     *
     * @param font 当前请求字体目录中的源对象
     * @param bytes 该字体已经读取并规范化的字节
     * @return 字体的 PostScript 名称
     * @throws IOException 名称解析失败时抛出，失败结果不进入缓存
     */
    public String postScriptName(PdfFont font, byte[] bytes) throws IOException {
        String name = postScriptNames.get(font);
        if (name == null) {
            name = PdfFontSource.postScriptName(bytes);
            postScriptNames.put(font, name);
        }
        return name;
    }

    /**
     * 提供上游排版器使用的字体度量缓存。
     *
     * @return 本次渲染各轮共用的缓存，其中保存不可变的字体度量
     */
    public FSCacheEx<String, FSCacheValue> metricsCache() {
        return metricsCache;
    }
}
