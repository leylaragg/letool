package com.github.leyland.letool.tool.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HTTP 便利能力的真实协议、资源边界和安全失败测试。
 */
class HttpUtilTest {

    /** 本地真实协议测试服务。 */
    private HttpServer server;

    /** 本地服务访问根地址。 */
    private String baseUrl;

    /** 当前用例创建的临时文件。 */
    private Path temporaryFile;

    /**
     * 为每个用例启动独立的回环地址 HTTP 服务，避免测试依赖外部网络。
     *
     * @throws IOException 本地测试服务无法创建时抛出
     */
    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 在用例完成后释放本地监听端口。
     */
    @AfterEach
    void stopServer() {
        server.stop(0);
        if (temporaryFile != null) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException exception) {
                throw new AssertionError("无法清理 HTTP 测试临时文件", exception);
            }
        }
    }

    /**
     * 验证实例配置在构建后保持稳定，并提供适合常规业务调用的安全默认值。
     */
    @Test
    void shouldBuildImmutableHttpConfigWithSafeDefaults() {
        HttpConfig defaults = HttpConfig.defaults();
        HttpConfig customized = HttpConfig.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(8))
                .maxResponseBytes(2 * 1024 * 1024L)
                .redirectPolicy(HttpClient.Redirect.NORMAL)
                .build();

        assertThat(defaults.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.getMaxResponseBytes()).isEqualTo(16 * 1024 * 1024L);
        assertThat(defaults.getRedirectPolicy()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(customized.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(customized.getRequestTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(customized.getMaxResponseBytes()).isEqualTo(2 * 1024 * 1024L);
        assertThat(customized.getRedirectPolicy()).isEqualTo(HttpClient.Redirect.NORMAL);
    }

    /**
     * 验证无效超时和响应上限在配置构建阶段立即失败。
     */
    @Test
    void shouldRejectInvalidHttpConfigValues() {
        assertThatThrownBy(() -> HttpConfig.builder().connectTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");
        assertThatThrownBy(() -> HttpConfig.builder().requestTimeout(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestTimeout");
        assertThatThrownBy(() -> HttpConfig.builder().maxResponseBytes(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResponseBytes");
        assertThatThrownBy(() -> HttpConfig.builder()
                .maxResponseBytes(HttpConfig.MAX_RESPONSE_BYTES + 1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResponseBytes");
    }

    /**
     * 验证查询参数编码、请求头、响应状态、响应头和响应体经过真实 HTTP 协议传输。
     */
    @Test
    void shouldExecuteRealHttpRequestAndReturnImmutableResponse() {
        server.createContext("/echo", exchange -> {
            String responseBody = exchange.getRequestMethod()
                    + "|" + exchange.getRequestURI().getRawQuery()
                    + "|" + exchange.getRequestHeaders().getFirst("X-Request-Id");
            exchange.getResponseHeaders().add("X-Trace-Id", "trace-1");
            respond(exchange, 201, responseBody);
        });

        HttpResponse response = HttpUtil.create(baseUrl + "/echo?")
                .get()
                .queryParam("name", "张 三")
                .header("X-Request-Id", "request-1")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody())
                .isEqualTo("GET|name=%E5%BC%A0%20%E4%B8%89|request-1");
        assertThat(response.header("X-Trace-Id")).isEqualTo("trace-1");
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getDuration()).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(response.getHeaders()).isUnmodifiable();
    }

    /**
     * 验证响应体超过实例配置上限时立即失败，并且异常不泄露请求地址。
     */
    @Test
    void shouldRejectOversizedResponseWithoutLeakingRequestUrl() {
        server.createContext("/large", exchange -> respond(exchange, 200, "0123456789"));
        HttpTemplate template = new HttpTemplate(HttpConfig.builder().maxResponseBytes(8).build());

        HttpException exception = catchThrowableOfType(
                () -> template.create(baseUrl + "/large?token=secret").get().execute(),
                HttpException.class);

        assertThat(exception.getCode()).isEqualTo(HttpErrorCode.RESPONSE_TOO_LARGE.getCode());
        assertThat(exception.getMessage()).doesNotContain("token", "secret", baseUrl);
    }

    /**
     * 验证非法请求地址使用稳定错误码失败，并且不会尝试连接外部目标。
     */
    @Test
    void shouldRejectInvalidUrlWithStableErrorCode() {
        HttpException exception = catchThrowableOfType(
                () -> HttpUtil.create("not-a-http-url?token=secret").get().execute(),
                HttpException.class);

        assertThat(exception.getCode()).isEqualTo(HttpErrorCode.INVALID_REQUEST.getCode());
        assertThat(exception.getMessage()).doesNotContain("token", "secret");
    }

    /**
     * 验证 Multipart 文件按真实协议发送、中文文件名保持 UTF-8，并执行请求生命周期拦截器。
     *
     * @throws IOException 临时文件写入失败时抛出
     */
    @Test
    void shouldSendMultipartWithUtf8FilenameAndInvokeInterceptors() throws IOException {
        Path testTempDirectory = Path.of("target", "test-tmp");
        Files.createDirectories(testTempDirectory);
        Path file = testTempDirectory.resolve(UUID.randomUUID() + "-报告.txt");
        temporaryFile = file;
        Files.writeString(file, "file-content", StandardCharsets.UTF_8);
        List<String> events = new ArrayList<>();
        server.createContext("/multipart", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String responseBody = exchange.getRequestHeaders().getFirst("X-Interceptor") + "|" + requestBody;
            respond(exchange, 200, responseBody);
        });

        HttpResponse response = HttpUtil.create(baseUrl + "/multipart")
                .post()
                .formField("title", "月报")
                .formFile("file", file)
                .interceptor(new HttpInterceptor() {
                    /** {@inheritDoc} */
                    @Override
                    public void beforeRequest(HttpRequest request) {
                        events.add("before");
                        request.header("X-Interceptor", "enabled");
                    }

                    /** {@inheritDoc} */
                    @Override
                    public void afterResponse(HttpRequest request, HttpResponse httpResponse) {
                        events.add("after");
                    }
                })
                .execute();

        assertThat(response.getBody())
                .contains("enabled|")
                .contains("name=\"title\"")
                .contains("月报")
                .contains("filename=\"" + file.getFileName() + "\"")
                .contains("file-content")
                .doesNotContain("filename*=");
        assertThat(events).containsExactly("before", "after");
    }

    /**
     * 验证显式状态码重试只自动作用于幂等请求，并准确记录尝试次数。
     */
    @Test
    void shouldRetryIdempotentRequestButNotPostByDefault() {
        AtomicInteger getAttempts = new AtomicInteger();
        AtomicInteger postAttempts = new AtomicInteger();
        server.createContext("/retry-get", exchange -> {
            int current = getAttempts.incrementAndGet();
            respond(exchange, current == 1 ? 503 : 200, current == 1 ? "retry" : "ok");
        });
        server.createContext("/retry-post", exchange -> {
            postAttempts.incrementAndGet();
            respond(exchange, 503, "not-retried");
        });

        HttpResponse getResponse = HttpUtil.create(baseUrl + "/retry-get")
                .get()
                .maxRetry(1)
                .retryOn(503)
                .retryDelay(Duration.ZERO)
                .execute();
        HttpResponse postResponse = HttpUtil.create(baseUrl + "/retry-post")
                .post()
                .maxRetry(1)
                .retryOn(503)
                .retryDelay(Duration.ZERO)
                .execute();

        assertThat(getResponse.getBody()).isEqualTo("ok");
        assertThat(getResponse.getAttempts()).isEqualTo(2);
        assertThat(getAttempts).hasValue(2);
        assertThat(postResponse.getStatusCode()).isEqualTo(503);
        assertThat(postResponse.getAttempts()).isEqualTo(1);
        assertThat(postAttempts).hasValue(1);
    }

    /**
     * 向本地测试客户端写入 UTF-8 文本响应。
     *
     * @param exchange 当前 HTTP 交换对象
     * @param status HTTP 响应状态码
     * @param body UTF-8 响应正文
     * @throws IOException 响应写入失败时抛出
     */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

}
