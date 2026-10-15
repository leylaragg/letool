package io.github.leylaragg.letool.print.pdf;

import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.exception.PrintResourceException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 字体真实解析和字符覆盖测试。
 *
 * @author leyland
 */
class PdfFontResourceValidatorTest {

    /** 可解析字体覆盖探测文本时，校验器会关闭本次打开的字体流。 */
    @Test
    void shouldParseRealFontAndCloseStream() {
        TrackingInputStream stream = new TrackingInputStream(openTestFont());
        PdfFontCatalog catalog = PdfFontCatalog.of(List.of(new PdfFont(
                "Droid Sans Fallback", FontWeight.NORMAL, () -> stream, true)));

        assertThatCode(() -> new PdfFontResourceValidator(catalog).validate("中文"))
                .doesNotThrowAnyException();
        assertThat(stream.closed).isTrue();
    }

    /** TTC 与渲染时一样读取首个字体面；同一字体源只打开一次并及时关闭。 */
    @Test
    void shouldParseTrueTypeCollectionAndCloseSource() throws IOException {
        byte[] collection = testFontCollection();
        List<TrackingInputStream> streams = new ArrayList<>();
        PdfFont font = new PdfFont("Collection Font", FontWeight.NORMAL, () -> {
            TrackingInputStream stream = new TrackingInputStream(new ByteArrayInputStream(collection));
            streams.add(stream);
            return stream;
        }, true);

        assertThatCode(() -> new PdfFontResourceValidator(PdfFontCatalog.of(List.of(font))).validate("中文"))
                .doesNotThrowAnyException();
        assertThat(streams).hasSize(1).allMatch(stream -> stream.closed);
    }

    /** 损坏字体和缺失字符都使用安全资源异常，不回显供应器或探测内容。 */
    @Test
    void shouldHideBrokenFontAndUnsupportedCharacterDetails() {
        PdfFontCatalog broken = PdfFontCatalog.of(List.of(new PdfFont(
                "Broken", FontWeight.NORMAL,
                () -> new ByteArrayInputStream("secret-font-data".getBytes()), true)));
        assertThatThrownBy(() -> new PdfFontResourceValidator(broken).validate(""))
                .isInstanceOf(PrintResourceException.class)
                .hasMessageNotContaining("secret-font-data")
                .hasCauseInstanceOf(IOException.class);

        PdfFontCatalog valid = PdfFontCatalog.of(List.of(new PdfFont(
                "Droid Sans Fallback", FontWeight.NORMAL, this::openTestFont, true)));
        String unsupported = new String(Character.toChars(Character.MAX_CODE_POINT));
        assertThatThrownBy(() -> new PdfFontResourceValidator(valid).validate(unsupported))
                .isInstanceOf(PrintResourceException.class)
                .hasMessageNotContaining(unsupported);
    }

    /** 字体供应器的空值和实现异常只留在受控原因链中。 */
    @Test
    void shouldHideFontSupplierFailure() {
        PdfFont missing = new PdfFont(
                "Missing", FontWeight.NORMAL, () -> null, false);
        assertThatThrownBy(() -> new PdfFontResourceValidator(
                PdfFontCatalog.of(List.of(missing))).validate(""))
                .isInstanceOf(PrintResourceException.class)
                .hasMessageNotContaining("字体流不能为空")
                .hasCauseInstanceOf(IllegalStateException.class);

        PdfFont failed = new PdfFont("Failed", FontWeight.NORMAL, () -> {
            throw new IllegalArgumentException("secret-font-supplier");
        }, false);
        assertThatThrownBy(() -> new PdfFontResourceValidator(
                PdfFontCatalog.of(List.of(failed))).validate(""))
                .isInstanceOf(PrintResourceException.class)
                .hasMessageNotContaining("secret-font-supplier")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    /** 探测文本本身需要有界且不能携带控制字符。 */
    @Test
    void shouldRejectUnsafeProbeTextBeforeOpeningFonts() {
        PdfFontCatalog catalog = PdfFontCatalog.of(List.of(new PdfFont(
                "Droid Sans Fallback", FontWeight.NORMAL, this::openTestFont, true)));
        PdfFontResourceValidator validator = new PdfFontResourceValidator(catalog);

        assertThatThrownBy(() -> validator.validate("line\nfeed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("探测文本");
        assertThatThrownBy(() -> validator.validate("A".repeat(4_097)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("探测文本");
    }

    /** 使用现有开源测试字体构造单面 TTC，避免测试依赖宿主安装的商业字体。 */
    private byte[] testFontCollection() throws IOException {
        byte[] font;
        try (InputStream input = openTestFont()) {
            font = input.readAllBytes();
        }

        // TTC 头包含签名、版本、字体面数量和首面偏移，共四个四字节字段。
        int faceOffset = 4 * Integer.BYTES;
        ByteBuffer collection = ByteBuffer.allocate(faceOffset + font.length);
        collection.putInt(0x74746366).putInt(0x00010000).putInt(1).putInt(faceOffset).put(font);

        // TTC 表记录的偏移相对整个文件；将原独立 TTF 的所有表偏移移过集合头。
        int tableCount = Short.toUnsignedInt(collection.getShort(faceOffset + 4));
        for (int index = 0; index < tableCount; index++) {
            int offsetField = faceOffset + 12 + index * 16 + 8;
            collection.putInt(offsetField, collection.getInt(offsetField) + faceOffset);
        }
        return collection.array();
    }

    /** 从测试资源打开一份可解析的真实字体。 */
    private InputStream openTestFont() {
        InputStream stream = getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf");
        if (stream == null) {
            throw new IllegalStateException("测试字体不存在");
        }
        return stream;
    }

    /** 包装真实字体流并记录资源归还。 */
    private static final class TrackingInputStream extends InputStream {

        /** 被包装的真实字体流。 */
        private final InputStream delegate;

        /** 校验完成后应被置为 {@code true}。 */
        private boolean closed;

        /** 创建可观察关闭状态的字体流。 */
        private TrackingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }
}
