package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.tool.enums.HttpMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpUtilTest {

    @Test
    void createRequest() {
        HttpRequest request = HttpUtil.create("http://example.com/api")
                .method(HttpMethod.GET)
                .header("X-Custom", "value")
                .connectTimeout(java.time.Duration.ofSeconds(5));
        assertEquals("http://example.com/api", request.getUrl());
        assertEquals(HttpMethod.GET, request.getMethod());
        assertEquals("value", request.getHeaders().get("X-Custom"));
    }

    @Test
    void chainApi() {
        HttpRequest request = HttpUtil.create()
                .url("http://example.com/test")
                .post()
                .contentType("application/json")
                .body("{\"key\":\"value\"}")
                .bearerToken("token123")
                .maxRetry(3);
        assertEquals("http://example.com/test", request.getUrl());
        assertEquals(HttpMethod.POST, request.getMethod());
        assertEquals("{\"key\":\"value\"}", request.getBody());
    }

    @Test
    void queryParams() {
        HttpRequest request = HttpUtil.create("http://example.com/search")
                .queryParam("page", 1)
                .queryParam("size", 20);
        assertEquals(2, request.getQueryParams().size());
        assertEquals(1, request.getQueryParams().get("page"));
    }

    @Test
    void globalConfig() {
        HttpConfig config = HttpUtil.getGlobalConfig();
        assertNotNull(config);
        assertTrue(config.getConnectTimeout().toMillis() > 0);
    }
}
