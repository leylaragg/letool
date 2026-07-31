package com.github.leyland.letool.net.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 常用无状态载荷编解码器工厂。
 */
public final class PayloadCodecs {

    /** 字节数组编解码器单例。 */
    private static final PayloadCodec<byte[], byte[]> BYTE_ARRAY_CODEC =
            new PayloadCodec<>() {
                /**
                 * 防御性复制请求字节。
                 *
                 * @param request 请求字节
                 * @return 请求字节副本
                 */
                @Override
                public byte[] encode(byte[] request) {
                    if (request == null) {
                        throw new IllegalArgumentException("request 不能为空");
                    }
                    return Arrays.copyOf(request, request.length);
                }

                /**
                 * 防御性复制响应字节。
                 *
                 * @param response 响应字节
                 * @return 响应字节副本
                 */
                @Override
                public byte[] decode(byte[] response) {
                    return Arrays.copyOf(response, response.length);
                }
            };

    private PayloadCodecs() {
    }

    /**
     * 获取防御性复制字节数组的编解码器。
     *
     * @return 线程安全的字节数组编解码器
     */
    public static PayloadCodec<byte[], byte[]> bytes() {
        return BYTE_ARRAY_CODEC;
    }

    /**
     * 创建 UTF-8 文本编解码器。
     *
     * @return UTF-8 文本编解码器
     */
    public static PayloadCodec<String, String> utf8() {
        return strings(StandardCharsets.UTF_8);
    }

    /**
     * 创建指定字符集的文本编解码器。
     *
     * @param charset 文本字符集
     * @return 线程安全的文本编解码器
     */
    public static PayloadCodec<String, String> strings(Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("charset 不能为空");
        }
        return new PayloadCodec<>() {
            /**
             * 将文本编码为字节。
             *
             * @param request 请求文本
             * @return 编码后的字节数组
             */
            @Override
            public byte[] encode(String request) {
                if (request == null) {
                    throw new IllegalArgumentException("request 不能为空");
                }
                return request.getBytes(charset);
            }

            /**
             * 将响应字节解码为文本。
             *
             * @param response 响应字节
             * @return 解码后的文本
             */
            @Override
            public String decode(byte[] response) {
                return new String(response, charset);
            }
        };
    }
}
