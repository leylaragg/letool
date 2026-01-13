package com.github.leyland.letool.letool.tool.Interceptor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @ClassName <h2>CustomClientHttpResponseWrapper</h2>
 * @Description 为了避免全局使用 BufferingClientHttpRequestFactory - ByteArrayInputStream, 自主选择构建数组流用于改变原有的 EofSensorInputStream
 * @Author rungo
 * @Date 3/12/2025
 * @Version 1.0
 **/
public class CustomClientHttpResponseWrapper implements ClientHttpResponse {

    private final ClientHttpResponse original;

    private byte[] body;

    public CustomClientHttpResponseWrapper(ClientHttpResponse original, byte[] body) {
        this.original = original;
        this.body = body;
    }

    // 方便构造，先把原始响应体缓存到 byte 数组里
    public CustomClientHttpResponseWrapper(ClientHttpResponse original) throws IOException {
        this.original = original;
        this.body = StreamUtils.copyToByteArray(original.getBody());
    }

    @Override
    public HttpStatus getStatusCode() throws IOException {
        return original.getStatusCode();
    }

    @Override
    public int getRawStatusCode() throws IOException {
        return original.getRawStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return original.getStatusText();
    }

    @Override
    public void close() {
        original.close();
    }

    @Override
    public InputStream getBody() throws IOException {
        if (this.body == null) {
            this.body = StreamUtils.copyToByteArray(this.original.getBody());
        }
        return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
        return original.getHeaders();
    }
}

