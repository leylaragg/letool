package io.github.leylaragg.letool.print.pdf.internal.font;

import io.github.leylaragg.letool.print.document.style.FontWeight;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.pdf.PdfFontCatalog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证请求内字体字节预算、独立输入流及全字体嵌入的源身份校验。
 *
 * @author leyland
 */
class PdfFontResourcesTest {

    @Test
    void shouldReuseFontBytesBySourceIdentity() throws IOException {
        AtomicInteger firstOpens = new AtomicInteger();
        AtomicInteger secondOpens = new AtomicInteger();
        PdfFont first = font("First", new byte[]{1, 2, 3}, firstOpens);
        PdfFont second = font("First", new byte[]{4, 5, 6}, secondOpens);
        PdfFontResources resources = new PdfFontResources(6);

        assertThat(resources.read(first)).containsExactly(1, 2, 3);
        assertThat(resources.read(second)).containsExactly(4, 5, 6);
        assertThat(resources.read(first)).containsExactly(1, 2, 3);
        assertThat(resources.read(second)).containsExactly(4, 5, 6);
        assertThat(firstOpens.get()).isEqualTo(1);
        assertThat(secondOpens.get()).isEqualTo(1);
    }

    @Test
    void shouldDisableBinaryCacheWhenBudgetIsZero() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        PdfFont source = font("First", new byte[]{1, 2, 3}, opens);
        PdfFontResources resources = new PdfFontResources(0);

        assertThat(resources.read(source)).containsExactly(1, 2, 3);
        assertThat(resources.read(source)).containsExactly(1, 2, 3);
        assertThat(opens.get()).isEqualTo(2);
    }

    @Test
    void shouldRetainOnlyFontsThatFitRemainingBudget() throws IOException {
        AtomicInteger firstOpens = new AtomicInteger();
        AtomicInteger secondOpens = new AtomicInteger();
        PdfFont first = font("First", new byte[]{1, 2, 3}, firstOpens);
        PdfFont second = font("Second", new byte[]{4, 5, 6}, secondOpens);
        PdfFontResources resources = new PdfFontResources(5);

        resources.read(first);
        resources.read(second);
        resources.read(first);
        assertThat(resources.read(second)).containsExactly(4, 5, 6);
        assertThat(firstOpens.get()).isEqualTo(1);
        assertThat(secondOpens.get()).isEqualTo(2);
    }

    @Test
    void shouldLeaveBudgetAvailableAfterUncachedLargeFont() throws IOException {
        AtomicInteger largeOpens = new AtomicInteger();
        AtomicInteger smallOpens = new AtomicInteger();
        PdfFont large = font("Large", new byte[]{1, 2, 3}, largeOpens);
        PdfFont small = font("Small", new byte[]{4, 5}, smallOpens);
        PdfFontResources resources = new PdfFontResources(2);

        resources.read(large);
        resources.read(small);
        resources.read(large);
        assertThat(resources.read(small)).containsExactly(4, 5);
        assertThat(largeOpens.get()).isEqualTo(2);
        assertThat(smallOpens.get()).isEqualTo(1);
    }

    @Test
    void shouldNotCacheFailedSourceReadAndShouldCloseFailedStream() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        PdfFont font = new PdfFont("Retry", FontWeight.NORMAL, () -> {
            if (opens.incrementAndGet() != 1) {
                return new ByteArrayInputStream(new byte[]{1, 2, 3});
            }
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("source unavailable");
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
        }, true);
        PdfFontResources resources = new PdfFontResources(3);

        assertThatThrownBy(() -> resources.read(font))
                .isInstanceOf(IOException.class).hasMessage("source unavailable");
        assertThat(resources.read(font)).containsExactly(1, 2, 3);
        assertThat(resources.read(font)).containsExactly(1, 2, 3);
        assertThat(opens.get()).isEqualTo(2);
        assertThat(closes.get()).isEqualTo(1);
    }

    @Test
    void shouldGiveEachConsumerIndependentStreamPositionAndLifetime() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        PdfFont source = font("First", new byte[]{1, 2, 3}, opens);
        PdfFontResources resources = new PdfFontResources(3);

        try (InputStream first = resources.openStream(source); InputStream second = resources.openStream(source)) {
            assertThat(first.read()).isEqualTo(1);
            assertThat(first.read()).isEqualTo(2);
            assertThat(second.read()).isEqualTo(1);
            first.close();
            assertThat(second.readAllBytes()).containsExactly(2, 3);
        }
        assertThat(opens.get()).isEqualTo(1);
    }

    @Test
    void shouldResolvePostScriptNameLazilyWithoutReopeningRetainedSource() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        PdfFont source = font("Droid", testFont(), opens);
        PdfFontResources resources = new PdfFontResources(64L * 1024 * 1024);

        resources.read(source);
        assertThat(resources.postScriptName(source)).isEqualTo("DroidSansFallback");
        assertThat(resources.postScriptName(source)).isEqualTo("DroidSansFallback");
        assertThat(opens.get()).isEqualTo(1);
    }

    @Test
    void shouldRetainPostScriptNameWithoutRetainingBinaryWhenDisabled() throws IOException {
        AtomicInteger opens = new AtomicInteger();
        PdfFont source = font("Droid", testFont(), opens);
        PdfFontResources resources = new PdfFontResources(0);

        assertThat(resources.postScriptName(source)).isEqualTo("DroidSansFallback");
        assertThat(resources.postScriptName(source)).isEqualTo("DroidSansFallback");
        resources.read(source);
        assertThat(opens.get()).isEqualTo(2);
    }

    @Test
    void shouldRejectDifferentBytesForSamePostScriptIdentityDuringFullEmbedding() throws IOException {
        byte[] first = testFont();
        byte[] second = Arrays.copyOf(first, first.length + 1);
        PdfFontCatalog catalog = PdfFontCatalog.of(List.of(
                font("First", first, new AtomicInteger()), font("Second", second, new AtomicInteger())));
        try (PDDocument pdf = new PDDocument()) {
            assertThatThrownBy(() -> new PdfFullFontWriter(catalog, new PdfFontResources(64L * 1024 * 1024)).write(pdf))
                    .isInstanceOf(IOException.class).hasMessage("字体源身份冲突");
        }
    }

    private PdfFont font(String family, byte[] bytes, AtomicInteger opens) {
        return new PdfFont(family, FontWeight.NORMAL, () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(bytes);
        }, false);
    }

    private byte[] testFont() throws IOException {
        try (InputStream source = Objects.requireNonNull(getClass().getResourceAsStream("/fonts/DroidSansFallback.ttf"))) {
            return source.readAllBytes();
        }
    }
}
