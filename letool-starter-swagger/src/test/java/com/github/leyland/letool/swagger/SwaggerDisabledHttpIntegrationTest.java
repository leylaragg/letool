package com.github.leyland.letool.swagger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Letool Swagger 关闭语义的真实 HTTP 集成测试。
 */
@SpringBootTest(
        classes = SwaggerDisabledHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "letool.swagger.enabled=false",
                "server.servlet.context-path=/test-app",
                "spring.mvc.servlet.path=/api",
                "springdoc.api-docs.path=/internal/openapi",
                "springdoc.swagger-ui.path=/internal/swagger"
        })
@DisplayName("Swagger 关闭后的真实 HTTP 端点")
class SwaggerDisabledHttpIntegrationTest {

    /** 关闭后必须隐藏的 API 文档入口。 */
    private static final List<String> DOCUMENT_PATHS = List.of(
            "/doc.html",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs.yaml/business-group",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/internal/openapi",
            "/internal/openapi.yaml",
            "/internal/openapi/swagger-config",
            "/internal/openapi/business-group",
            "/internal/openapi.yaml/business-group",
            "/internal/swagger");

    /** 测试应用随机分配的 HTTP 端口。 */
    @LocalServerPort
    private int port;

    /** 面向当前测试应用的 HTTP 客户端。 */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 验证关闭 Letool Swagger 后所有已知文档入口均返回 404。
     *
     * @throws IOException HTTP 请求读写失败时抛出
     * @throws InterruptedException HTTP 请求被中断时抛出
     */
    @Test
    @DisplayName("关闭后所有文档入口返回 404")
    void shouldHideAllDocumentEndpoints() throws IOException, InterruptedException {
        for (String path : DOCUMENT_PATHS) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode())
                    .as("文档入口 %s 应返回 404", path)
                    .isEqualTo(404);
        }
    }

    /**
     * 验证关闭文档能力不会误伤普通业务接口。
     *
     * @throws IOException HTTP 请求读写失败时抛出
     * @throws InterruptedException HTTP 请求被中断时抛出
     */
    @Test
    @DisplayName("关闭文档后业务接口仍可访问")
    void shouldKeepBusinessEndpointAvailable() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/business/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("pong");
    }

    /**
     * 验证自定义文档路径不会误伤使用相同路径前缀的业务接口。
     *
     * @throws IOException HTTP 请求读写失败时抛出
     * @throws InterruptedException HTTP 请求被中断时抛出
     */
    @Test
    @DisplayName("自定义文档路径不误伤同前缀业务接口")
    void shouldKeepOverlappingBusinessPathAvailable()
            throws IOException, InterruptedException {
        HttpResponse<String> response = get("/internal/openapi/orders/42");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("order-42");
    }

    /**
     * 发送 GET 请求到当前随机端口下的指定路径。
     *
     * @param path 请求路径
     * @return HTTP 字符串响应
     * @throws IOException HTTP 请求读写失败时抛出
     * @throws InterruptedException HTTP 请求被中断时抛出
     */
    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/test-app/api" + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 仅用于验证 Swagger 关闭行为的最小 Servlet 测试应用。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(BusinessController.class)
    static class TestApplication {

        /**
         * 创建真实 Springdoc 分组，用于验证自定义分组端点被关闭。
         *
         * @return 业务接口文档分组
         */
        @Bean
        GroupedOpenApi businessGroupedOpenApi() {
            return GroupedOpenApi.builder()
                    .group("business-group")
                    .pathsToMatch("/business/**")
                    .build();
        }
    }

    /**
     * 提供一个不属于文档端点的最小业务接口。
     */
    @RestController
    static class BusinessController {

        /**
         * 返回固定业务响应。
         *
         * @return 固定的存活响应
         */
        @GetMapping("/business/ping")
        String ping() {
            return "pong";
        }

        /**
         * 返回与自定义文档路径共享前缀的业务响应。
         *
         * @return 固定的订单响应
         */
        @GetMapping("/internal/openapi/orders/42")
        String order() {
            return "order-42";
        }
    }
}
