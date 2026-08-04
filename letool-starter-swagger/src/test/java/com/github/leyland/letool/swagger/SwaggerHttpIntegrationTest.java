package com.github.leyland.letool.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Swagger 文档 JSON 与用户界面的真实 HTTP 集成测试。
 */
@SpringBootTest(
        classes = SwaggerHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "letool.swagger.title=集成测试 API",
                "letool.swagger.version=9.0.0",
                "letool.swagger.security.bearer-token=true"
        })
@DisplayName("Swagger 真实 HTTP 端点")
class SwaggerHttpIntegrationTest {

    /** 测试应用随机分配的 HTTP 端口。 */
    @LocalServerPort
    private int port;

    /** 面向当前测试应用的 HTTP 客户端。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * 验证 OpenAPI JSON 包含 Letool 文档信息、测试接口与 Bearer 安全方案。
     */
    @Test
    @DisplayName("OpenAPI JSON 暴露最终文档契约")
    void shouldExposeConfiguredOpenApiDocument() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                url("/v3/api-docs"), JsonNode.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType())
                .matches(contentType -> contentType != null
                        && MediaType.APPLICATION_JSON.isCompatibleWith(contentType));

        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("info").path("title").asText()).isEqualTo("集成测试 API");
        assertThat(body.path("info").path("version").asText()).isEqualTo("9.0.0");
        assertThat(body.path("paths").has("/test/ping")).isTrue();

        JsonNode bearerScheme = body.path("components")
                .path("securitySchemes")
                .path("BearerAuth");
        assertThat(bearerScheme.isObject()).isTrue();
        assertThat(bearerScheme.path("type").asText()).isEqualTo("http");
        assertThat(bearerScheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerScheme.path("bearerFormat").asText()).isEqualTo("JWT");

        JsonNode globalSecurity = body.path("security");
        assertThat(globalSecurity.isArray()).isTrue();
        assertThat(globalSecurity.size()).isEqualTo(1);
        assertThat(globalSecurity.path(0).path("BearerAuth").isArray()).isTrue();
        assertThat(globalSecurity.path(0).path("BearerAuth").isEmpty()).isTrue();
    }

    /**
     * 验证 Swagger UI 入口严格重定向到可访问的 HTML 页面。
     *
     * @throws IOException HTTP 请求读写失败时抛出
     * @throws InterruptedException HTTP 请求被中断时抛出
     */
    @Test
    @DisplayName("Swagger UI 入口重定向到 HTML 页面")
    void shouldRedirectToSwaggerUiIndex() throws IOException, InterruptedException {
        HttpClient redirectClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest redirectRequest = HttpRequest.newBuilder()
                .uri(URI.create(url("/swagger-ui.html")))
                .GET()
                .build();
        HttpResponse<String> redirectResponse = redirectClient.send(
                redirectRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(redirectResponse.statusCode()).isBetween(300, 399);
        String locationValue = redirectResponse.headers()
                .firstValue("Location")
                .orElse(null);
        assertThat(locationValue).isNotBlank();
        URI location = URI.create(locationValue);
        assertThat(location.getPath()).isEqualTo("/swagger-ui/index.html");

        ResponseEntity<String> pageResponse = restTemplate.getForEntity(
                resolve(location), String.class);

        assertThat(pageResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pageResponse.getHeaders().getContentType())
                .matches(contentType -> contentType != null
                        && MediaType.TEXT_HTML.isCompatibleWith(contentType));
        assertThat(pageResponse.getBody()).contains("Swagger UI");
    }

    /**
     * 拼接当前随机端口下的本地测试地址。
     *
     * @param path 请求路径
     * @return 可供测试客户端访问的完整地址
     */
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 将响应中的重定向地址解析为当前测试服务可访问的绝对地址。
     *
     * @param location 响应中的重定向地址
     * @return 可供测试客户端访问的绝对地址
     */
    private URI resolve(URI location) {
        return location.isAbsolute() ? location : URI.create(url(location.toString()));
    }

    /**
     * 仅用于启动 Swagger HTTP 端点的最小 Servlet 测试应用。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApplication {
    }

    /**
     * 提供一个可被 Springdoc 扫描的最小测试接口。
     */
    @RestController
    static class TestController {

        /**
         * 返回固定响应以声明一个真实请求映射。
         *
         * @return 固定的存活响应
         */
        @GetMapping("/test/ping")
        String ping() {
            return "pong";
        }
    }
}
