package io.github.leylaragg.letool.print.pdf.internal.font;

import io.github.leylaragg.letool.print.pdf.PdfFont;

import io.github.leylaragg.letool.print.document.style.FontWeight;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TTC 只读取首面，并完整保留首面的字体表、字形编号和变体映射。
 *
 * @author leyland
 */
class PdfFontSourceTest {

    /** 未使用的第二面把 post 表放在文件末尾，复现 NSimSun 的解析警告。 */
    @Test
    void shouldReadFirstFaceWithoutParsingUnusedPostScriptTable() throws IOException {
        byte[] collection = fontCollection(testFont());
        Logger logger = Logger.getLogger("org.apache.fontbox.ttf.PostScriptTable");
        List<String> warnings = new ArrayList<>();
        Handler handler = warningRecorder(warnings);
        logger.addHandler(handler);
        try {
            byte[] selected = read(collection);

            assertThat(PdfFontSource.postScriptName(selected)).isEqualTo("DroidSansFallback");
            assertThat(warnings).isEmpty();
        } finally {
            logger.removeHandler(handler);
        }
    }

    /** 首面的所有表内容保持一致；只重算 head 校验修正值并移除失效签名。 */
    @Test
    void shouldPreserveFirstFaceTablesGlyphIdsAndFullEmbedding() throws IOException {
        byte[] original = testFont();
        byte[] selected = read(fontCollection(original));
        Map<String, byte[]> expected = tables(original, 0);
        Map<String, byte[]> actual = tables(selected, 0);
        expected.remove("DSIG");
        Arrays.fill(expected.get("head"), 8, 12, (byte) 0);
        Arrays.fill(actual.get("head"), 8, 12, (byte) 0);

        assertThat(actual.keySet()).isEqualTo(expected.keySet());
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertThat(actual.get(entry.getKey())).as("font table %s", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
        assertThat(checksum(selected)).isEqualTo(0xB1B0AFBAL);
        try (TrueTypeFont source = new TTFParser().parse(new RandomAccessReadBuffer(original));
                TrueTypeFont converted = new TTFParser().parse(new RandomAccessReadBuffer(selected));
                PDDocument document = new PDDocument()) {
            assertThat(converted.getName()).isEqualTo(source.getName());
            assertThat(converted.getNumberOfGlyphs()).isEqualTo(49_775);
            for (int codePoint : new int[]{0x4e2d, 0x6587, 0x9f99, 0x20000, 0x2a6d6}) {
                assertThat(converted.getUnicodeCmapLookup().getGlyphId(codePoint))
                        .isEqualTo(source.getUnicodeCmapLookup().getGlyphId(codePoint));
            }
            PDType0Font embedded = PDType0Font.load(document, new ByteArrayInputStream(selected), false);
            assertThat(embedded.isEmbedded()).isTrue();
            assertThat(embedded.willBeSubset()).isFalse();
            assertThat(embedded.getFontDescriptor().getFontFile2().toByteArray()).isEqualTo(selected);
        }
    }

    /** 首面含真实 Format14 非默认变体映射时，抽取过程也必须逐字节保留。 */
    @Test
    void shouldPreserveVariationMappingInSelectedFace() throws IOException {
        byte[] original = withVariationMapping(testFont());
        byte[] selected = read(fontCollection(original));

        assertThat(tables(selected, 0).get("cmap")).isEqualTo(tables(original, 0).get("cmap"));
        assertThat(checksum(selected)).isEqualTo(0xB1B0AFBAL);
    }

    /** 后续未选择字体面的损坏不应使可用首面失效。 */
    @Test
    void shouldIgnoreInvalidUnselectedFaceOffset() throws IOException {
        byte[] collection = fontCollection(testFont());
        ByteBuffer.wrap(collection).putInt(16, -1);

        assertThat(PdfFontSource.postScriptName(read(collection))).isEqualTo("DroidSansFallback");
    }

    /** 首面表越界属于真实字体损坏，不能按空表继续转换。 */
    @Test
    void shouldRejectOutOfBoundsSelectedTable() throws IOException {
        byte[] collection = fontCollection(testFont());
        int record = tableRecord(collection, 20, "cmap");
        ByteBuffer.wrap(collection).putInt(record + 8, collection.length - 2);

        assertThatThrownBy(() -> read(collection)).isInstanceOf(IOException.class);
    }

    /** 集合头缺失或首面偏移越界时，应报告受控读取异常。 */
    @Test
    void shouldRejectTruncatedCollectionAndInvalidFirstFace() throws IOException {
        assertThatThrownBy(() -> read(new byte[]{'t', 't', 'c', 'f'}))
                .isInstanceOf(IOException.class);
        byte[] collection = fontCollection(testFont());
        ByteBuffer.wrap(collection).putInt(12, -1);

        assertThatThrownBy(() -> read(collection)).isInstanceOf(IOException.class);
    }

    /** 保留记录器原有级别和处理链，测试只旁路收集真实 WARN。 */
    private Handler warningRecorder(List<String> warnings) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    /** 使用已有开源字体构造共享表的双面 TTC，无需商业字体或本机字体安装。 */
    private byte[] fontCollection(byte[] font) {
        int firstOffset = 20;
        int tableCount = Short.toUnsignedInt(ByteBuffer.wrap(font).getShort(4));
        int directoryLength = 12 + tableCount * 16;
        int secondOffset = aligned(firstOffset + font.length);
        int postOffset = secondOffset + directoryLength;
        ByteBuffer collection = ByteBuffer.allocate(postOffset + 32);
        collection.putInt(0x74746366).putInt(0x00010000).putInt(2)
                .putInt(firstOffset).putInt(secondOffset).put(font);
        System.arraycopy(font, 0, collection.array(), secondOffset, directoryLength);
        for (int faceOffset : new int[]{firstOffset, secondOffset}) {
            for (int index = 0; index < tableCount; index++) {
                int offsetField = faceOffset + 12 + index * 16 + 8;
                collection.putInt(offsetField, collection.getInt(offsetField) + firstOffset);
            }
        }
        byte[] post = tables(font, 0).get("post");
        System.arraycopy(post, 0, collection.array(), postOffset, 32);
        int secondPostRecord = tableRecord(collection.array(), secondOffset, "post");
        collection.putInt(secondPostRecord + 8, postOffset).putInt(secondPostRecord + 12, 32);
        return collection.array();
    }

    /** 增加中字符的 FE00 变体记录，断言首面抽取不会剔除 FontBox 尚未支持的数据。 */
    private byte[] withVariationMapping(byte[] font) {
        byte[] originalCmap = tables(font, 0).get("cmap");
        ByteBuffer source = ByteBuffer.wrap(originalCmap);
        int count = Short.toUnsignedInt(source.getShort(2));
        int directoryLength = 4 + count * 8;
        int variationOffset = originalCmap.length + 8;
        ByteBuffer cmap = ByteBuffer.allocate(variationOffset + 30);
        cmap.putShort((short) 0).putShort((short) (count + 1));
        for (int index = 0; index < count; index++) {
            int record = 4 + index * 8;
            cmap.putInt(source.getInt(record)).putInt(source.getInt(record + 4) + 8);
        }
        cmap.putShort((short) 0).putShort((short) 5).putInt(variationOffset);
        cmap.put(originalCmap, directoryLength, originalCmap.length - directoryLength);
        cmap.putShort((short) 14).putInt(30).putInt(1)
                .put(new byte[]{0x00, (byte) 0xfe, 0x00}).putInt(0).putInt(21)
                .putInt(1).put(new byte[]{0x00, 0x4e, 0x2d}).putShort((short) 587);
        int cmapOffset = aligned(font.length);
        byte[] changed = Arrays.copyOf(font, cmapOffset + cmap.capacity());
        System.arraycopy(cmap.array(), 0, changed, cmapOffset, cmap.capacity());
        int record = tableRecord(changed, 0, "cmap");
        ByteBuffer.wrap(changed).putInt(record + 4, (int) checksum(cmap.array()))
                .putInt(record + 8, cmapOffset).putInt(record + 12, cmap.capacity());
        return changed;
    }

    /** 获取指定字体面的原始表，比较时不借助会忽略 Format14 的语义解析器。 */
    private Map<String, byte[]> tables(byte[] font, int faceOffset) {
        ByteBuffer data = ByteBuffer.wrap(font);
        int tableCount = Short.toUnsignedInt(data.getShort(faceOffset + 4));
        Map<String, byte[]> result = new TreeMap<>();
        for (int index = 0; index < tableCount; index++) {
            int record = faceOffset + 12 + index * 16;
            String tag = new String(font, record, 4, StandardCharsets.US_ASCII);
            int offset = data.getInt(record + 8);
            int length = data.getInt(record + 12);
            result.put(tag, Arrays.copyOfRange(font, offset, offset + length));
        }
        return result;
    }

    /** 查找表记录在源数据中的位置。 */
    private int tableRecord(byte[] font, int faceOffset, String tag) {
        int count = Short.toUnsignedInt(ByteBuffer.wrap(font).getShort(faceOffset + 4));
        for (int index = 0; index < count; index++) {
            int record = faceOffset + 12 + index * 16;
            if (tag.equals(new String(font, record, 4, StandardCharsets.US_ASCII))) {
                return record;
            }
        }
        throw new IllegalArgumentException("测试字体缺少表 " + tag);
    }

    /** 按规范独立计算完整 sfnt 校验值。 */
    private long checksum(byte[] data) {
        long value = 0;
        for (int index = 0; index < data.length; index += 4) {
            long word = 0;
            for (int part = 0; part < 4; part++) {
                word = word << 8 | (index + part < data.length ? data[index + part] & 0xff : 0);
            }
            value = value + word & 0xffffffffL;
        }
        return value;
    }

    /** 对齐 TTC 字体目录与表数据。 */
    private int aligned(int value) {
        return (value + 3) & -4;
    }

    /** 每次提供独立流，与生产字体供应器一致。 */
    private byte[] read(byte[] data) throws IOException {
        return PdfFontSource.read(new PdfFont("Collection Font", FontWeight.NORMAL,
                () -> new ByteArrayInputStream(data), false));
    }

    /** 读取项目已有的完整开源测试字体。 */
    private byte[] testFont() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf")) {
            if (input == null) {
                throw new IOException("测试字体不存在");
            }
            return input.readAllBytes();
        }
    }
}
