package io.github.leylaragg.letool.print.pdf;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFTable;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * 从真实字体源读取 PostScript 身份；TTC 首面转换保持所有 GID。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfFontSource {
    /** 单个源字体最多读取 64 MiB，额外读取一个字节用于识别超限。 */
    private static final int MAX_SOURCE_BYTES = 64 * 1024 * 1024;

    /** TrueType 表目录的固定头长和单条记录长度，单位为字节。 */
    private static final int SFNT_HEADER_BYTES = 12;
    private static final int TABLE_RECORD_BYTES = 16;

    /** TrueType 表按四字节对齐，head 表在第八字节保存全字体校验修正值。 */
    private static final int TABLE_ALIGNMENT_BYTES = 4;
    private static final int CHECKSUM_ADJUSTMENT_OFFSET = 8;
    private static final long FONT_CHECKSUM = 0xB1B0AFBAL;

    private PdfFontSource() {
    }

    /**
     * 读取字体源，遇到 TTC 时保留首个字体面的全部字形编号。
     *
     * @param font 宿主注册的字体源；本方法打开并关闭其输入流
     * @return 可独立解析的 TrueType 字节，不与输入流共享生命周期
     * @throws IOException 读取失败、源文件超限或字体集合为空
     */
    static byte[] read(PdfFont font) throws IOException {
        byte[] raw;
        try (InputStream input = font.openStream()) {
            raw = input.readNBytes(MAX_SOURCE_BYTES + 1);
        }
        if (raw.length > MAX_SOURCE_BYTES) {
            throw new IOException("字体资源超过容量限制");
        }
        // TTC 不直接交给下游嵌入；先抽出首面，保留原始字形编号。
        if (raw.length >= 4 && raw[0] == 't' && raw[1] == 't' && raw[2] == 'c' && raw[3] == 'f') {
            byte[][] first = new byte[1][];
            try (TrueTypeCollection collection = new TrueTypeCollection(new ByteArrayInputStream(raw))) {
                collection.processAllFonts(face -> {
                    if (first[0] == null) {
                        first[0] = sfnt(face);
                    }
                });
            }
            if (first[0] == null) {
                throw new IOException("字体集合为空");
            }
            return first[0];
        }
        return raw;
    }

    /**
     * 从实际字体文件读取用于匹配 PDF 字体资源的 PostScript 名称。
     *
     * @param data 可独立解析的 TrueType 字节
     * @return 字体文件声明的 PostScript 名称
     * @throws IOException 字体解析或名称读取失败
     */
    static String postScriptName(byte[] data) throws IOException {
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(data))) {
            return font.getName();
        }
    }

    /** 重建 TTC 字体面的独立表目录；不做字形子集化，避免改变已有 GID。 */
    private static byte[] sfnt(TrueTypeFont font) throws IOException {
        // 表记录按标签排序；重建文件后原数字签名失效，因此不复制 DSIG。
        Map<String, byte[]> tables = new TreeMap<>();
        for (TTFTable table : font.getTables()) {
            if (!"DSIG".equals(table.getTag())) {
                tables.put(table.getTag(), font.getTableBytes(table));
            }
        }
        byte[] head = tables.get("head");
        if (head == null) {
            throw new IOException("字体缺少 head 表");
        }

        // 计算字体总校验值前，head 中的修正字段必须清零。
        Arrays.fill(head, CHECKSUM_ADJUSTMENT_OFFSET, CHECKSUM_ADJUSTMENT_OFFSET + Integer.BYTES, (byte) 0);

        // 预留目录并按四字节边界安排表数据，保证偏移和校验值使用同一布局。
        int count = tables.size();
        int offset = SFNT_HEADER_BYTES + count * TABLE_RECORD_BYTES;
        int size = offset;
        for (byte[] table : tables.values()) {
            size = Math.addExact(size, (table.length + TABLE_ALIGNMENT_BYTES - 1) & -TABLE_ALIGNMENT_BYTES);
        }
        ByteBuffer result = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        int search = TABLE_RECORD_BYTES * Integer.highestOneBit(count);
        result.putInt(0x10000).putShort((short) count).putShort((short) search)
            .putShort((short) Integer.numberOfTrailingZeros(search / TABLE_RECORD_BYTES))
            .putShort((short) (TABLE_RECORD_BYTES * count - search));
        int headOffset = 0;
        for (Map.Entry<String, byte[]> entry : tables.entrySet()) {
            byte[] table = entry.getValue();
            result.put(entry.getKey().getBytes(StandardCharsets.US_ASCII))
                .putInt((int) checksum(table)).putInt(offset).putInt(table.length);
            System.arraycopy(table, 0, result.array(), offset, table.length);
            if ("head".equals(entry.getKey())) {
                headOffset = offset;
            }
            offset += (table.length + TABLE_ALIGNMENT_BYTES - 1) & -TABLE_ALIGNMENT_BYTES;
        }

        // 最后写入全文件校验修正值，不改变各表中的字形编号。
        result.putInt(headOffset + CHECKSUM_ADJUSTMENT_OFFSET, (int) (FONT_CHECKSUM - checksum(result.array())));
        return result.array();
    }

    /** 按大端无符号四字节累加，末尾不足四字节时补零。 */
    private static long checksum(byte[] data) {
        long sum = 0;
        for (int index = 0; index < data.length; index += Integer.BYTES) {
            long word = 0;
            for (int part = 0; part < Integer.BYTES; part++) {
                word = word << Byte.SIZE | (index + part < data.length ? data[index + part] & 0xff : 0);
            }
            sum = sum + word & 0xffffffffL;
        }
        return sum;
    }
}
