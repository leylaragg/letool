package com.github.leyland.letool.tool.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @ClassName <h2>IOUtil</h2>
 * @Description
 * @Author rungo
 * @Date 3/11/2025
 * @Version 1.0
 **/
public class IOUtil {

    // ✅ 复制 InputStream 到字节数组（不会影响原始流）
    public static byte[] cloneInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray(); // 返回流的完整数据
    }
}
