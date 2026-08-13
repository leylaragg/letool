package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.model.DigestCopyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DigestUtilTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sha256MatchesStandardVectorForAllInputs() throws IOException {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        Path path = Files.write(temporaryDirectory.resolve("content.bin"), content);

        assertEquals(ABC_SHA256, DigestUtil.sha256("abc"));
        assertEquals(ABC_SHA256, DigestUtil.sha256(content));
        assertEquals(ABC_SHA256, DigestUtil.sha256(new ByteArrayInputStream(content)));
        assertEquals(ABC_SHA256, DigestUtil.sha256(path));
    }

    @Test
    void copyAndSha256ConsumesInputOnlyOnce() throws IOException {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        DigestCopyResult result = DigestUtil.copyAndSha256(
                new ByteArrayInputStream(content), output);

        assertEquals(3, result.bytesCopied());
        assertEquals(ABC_SHA256, result.sha256());
        assertArrayEquals(content, output.toByteArray());
    }

    @Test
    void constantTimeComparisonAcceptsCaseAndRejectsInvalidOrDifferentDigest() {
        assertTrue(DigestUtil.matchesSha256(ABC_SHA256.toUpperCase(), ABC_SHA256));
        assertFalse(DigestUtil.matchesSha256(ABC_SHA256, "00".repeat(32)));
        assertFalse(DigestUtil.matchesSha256(ABC_SHA256, "invalid"));
        assertFalse(DigestUtil.matchesSha256(null, ABC_SHA256));
    }
}
