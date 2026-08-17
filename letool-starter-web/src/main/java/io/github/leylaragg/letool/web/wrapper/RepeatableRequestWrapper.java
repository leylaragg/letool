package io.github.leylaragg.letool.web.wrapper;

import io.github.leylaragg.letool.web.exception.RequestBodyTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 在受控大小内缓存请求体并支持多次读取的 HTTP 请求包装器。
 *
 * <p>构造阶段会消耗原始请求流，并同时校验声明长度和实际读取长度。每次调用
 * {@link #getInputStream()} 或 {@link #getReader()} 都从缓存起点创建独立读取器。</p>
 */
public final class RepeatableRequestWrapper extends HttpServletRequestWrapper {

    /** 单次实际读取使用的缓冲区大小。 */
    private static final int READ_BUFFER_SIZE = 8192;

    /** 缓存后的不可变请求体字节。 */
    private final byte[] body;

    /**
     * 缓存请求体并创建可重复读取包装器。
     *
     * @param request 原始 HTTP 请求
     * @param maxBodySize 允许缓存的最大字节数
     * @throws IOException 读取原始请求体失败时抛出
     * @throws RequestBodyTooLargeException 声明或实际请求体超过上限时抛出
     * @throws IllegalArgumentException 当上限不能用 Java 字节数组安全表达时抛出
     */
    public RepeatableRequestWrapper(HttpServletRequest request, long maxBodySize) throws IOException {
        super(Objects.requireNonNull(request, "request"));
        if (maxBodySize <= 0 || maxBodySize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBodySize must be between 1 and Integer.MAX_VALUE");
        }
        this.body = readBody(request, maxBodySize);
    }

    /**
     * 创建从缓存起点读取的新 Servlet 输入流。
     *
     * @return 独立的缓存请求体输入流
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyInputStream(body);
    }

    /**
     * 使用请求声明字符集创建从缓存起点读取的新字符流。
     *
     * @return 独立的缓存请求体字符流
     * @throws IOException 请求声明了 JVM 不支持的字符集时抛出
     */
    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), resolveCharset()));
    }

    /**
     * 同时根据声明长度和实际读取字节数缓存请求体。
     *
     * @param request 原始 HTTP 请求
     * @param maxBodySize 最大缓存字节数
     * @return 缓存后的请求体字节
     * @throws IOException 读取失败时抛出
     */
    private static byte[] readBody(HttpServletRequest request, long maxBodySize) throws IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBodySize) {
            throw new RequestBodyTooLargeException(declaredLength, maxBodySize);
        }

        int initialCapacity = declaredLength > 0
                ? (int) declaredLength
                : (int) Math.min(1024, maxBodySize);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
        ServletInputStream input = request.getInputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        long total = 0;
        while (true) {
            // 每次最多读取到上限后的第一个字节，确保未知长度请求及时终止。
            int allowedRead = (int) Math.min(buffer.length, maxBodySize - total + 1);
            int read = input.read(buffer, 0, allowedRead);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maxBodySize) {
                throw new RequestBodyTooLargeException(total, maxBodySize);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * 解析请求字符集，未声明时遵循 Servlet 默认字符集。
     *
     * @return 请求字符集
     * @throws UnsupportedEncodingException 请求声明了不支持的字符集时抛出
     */
    private Charset resolveCharset() throws UnsupportedEncodingException {
        String encoding = getCharacterEncoding();
        if (!StringUtils.hasText(encoding)) {
            return StandardCharsets.ISO_8859_1;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException exception) {
            UnsupportedEncodingException unsupported = new UnsupportedEncodingException(encoding);
            unsupported.initCause(exception);
            throw unsupported;
        }
    }

    /**
     * 基于内存字节数组实现的 Servlet 输入流。
     */
    private static final class CachedBodyInputStream extends ServletInputStream {

        /** 字节数组读取委托。 */
        private final ByteArrayInputStream delegate;

        /** 已注册的异步读取监听器。 */
        private ReadListener readListener;

        /**
         * 创建缓存请求体输入流。
         *
         * @param body 缓存请求体
         */
        private CachedBodyInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        /**
         * 判断缓存内容是否已经全部读取。
         *
         * @return 无剩余字节时返回 {@code true}
         */
        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        /**
         * 内存流始终可以立即读取。
         *
         * @return 始终返回 {@code true}
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * 注册非阻塞读取监听器并同步通知内存数据可用状态。
         *
         * @param listener 非空读取监听器
         * @throws IllegalArgumentException 当监听器为 {@code null} 时抛出
         * @throws IllegalStateException 当重复注册监听器时抛出
         */
        @Override
        public void setReadListener(ReadListener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("readListener must not be null");
            }
            if (readListener != null) {
                throw new IllegalStateException("readListener has already been registered");
            }
            readListener = listener;
            try {
                if (!isFinished()) {
                    listener.onDataAvailable();
                }
                if (isFinished()) {
                    listener.onAllDataRead();
                }
            } catch (Throwable throwable) {
                listener.onError(throwable);
            }
        }

        /**
         * 读取单个字节。
         *
         * @return 下一个无符号字节；流结束时返回 {@code -1}
         */
        @Override
        public int read() {
            return delegate.read();
        }

        /**
         * 批量读取缓存字节。
         *
         * @param target 目标缓冲区
         * @param offset 写入起始位置
         * @param length 最大读取长度
         * @return 实际读取长度；流结束时返回 {@code -1}
         */
        @Override
        public int read(byte[] target, int offset, int length) {
            return delegate.read(target, offset, length);
        }
    }
}
