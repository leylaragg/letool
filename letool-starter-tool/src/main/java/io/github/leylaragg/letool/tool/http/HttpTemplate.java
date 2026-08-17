package io.github.leylaragg.letool.tool.http;

import io.github.leylaragg.letool.tool.enums.HttpMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * 基于 JDK 17 {@link HttpClient} 的线程安全 HTTP 请求模板。
 *
 * <p>模板持有可复用的 JDK 客户端以复用连接和协议协商状态。常规调用可使用默认构造函数；需要代理、
 * 自定义 TLS、认证器或执行器时，可以构造 JDK 客户端后通过双参数构造函数注入。</p>
 */
public final class HttpTemplate {

    /** 模板基础配置。 */
    private final HttpConfig config;

    /** 线程安全且可复用的 JDK HTTP 客户端。 */
    private final HttpClient client;

    /**
     * 使用安全默认配置创建 HTTP 模板。
     */
    public HttpTemplate() {
        this(HttpConfig.defaults());
    }

    /**
     * 根据不可变配置创建 HTTP 模板和共享 JDK 客户端。
     *
     * @param config HTTP 基础配置
     */
    public HttpTemplate(HttpConfig config) {
        this(config, buildClient(config));
    }

    /**
     * 使用调用方提供的 JDK 客户端创建 HTTP 模板。
     *
     * <p>自定义客户端负责连接超时、重定向、代理、TLS 与认证等客户端级行为；模板配置仍负责请求总超时
     * 和响应体大小边界。</p>
     *
     * @param config HTTP 基础配置
     * @param client 调用方配置完成的线程安全 JDK HTTP 客户端
     */
    public HttpTemplate(HttpConfig config, HttpClient client) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * 创建尚未设置 URL 的请求构建器。
     *
     * @return 绑定当前模板的请求构建器
     */
    public HttpRequest create() {
        return HttpRequest.of(this, null);
    }

    /**
     * 创建带请求地址的请求构建器。
     *
     * @param url HTTP 或 HTTPS 地址
     * @return 绑定当前模板的请求构建器
     */
    public HttpRequest create(String url) {
        return HttpRequest.of(this, url);
    }

    /**
     * 获取模板使用的不可变配置。
     *
     * @return HTTP 基础配置
     */
    public HttpConfig getConfig() {
        return config;
    }

    /**
     * 执行链式请求构建器。
     *
     * @param request 待执行请求
     * @return 最终 HTTP 响应
     */
    HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<HttpInterceptor> interceptors = request.interceptorSnapshot();
        long startedAt = System.nanoTime();
        try {
            for (HttpInterceptor interceptor : interceptors) {
                interceptor.beforeRequest(request);
            }
        } catch (RuntimeException exception) {
            HttpException wrapped = HttpException.interceptorFailed(exception);
            notifyError(interceptors, request, wrapped);
            throw wrapped;
        }

        HttpRequest.Snapshot snapshot;
        try {
            snapshot = request.snapshot(config);
        } catch (HttpException exception) {
            notifyError(interceptors, request, exception);
            throw exception;
        }

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                java.net.http.HttpResponse<byte[]> response = client.send(
                        buildRequest(snapshot),
                        limitedBodyHandler(config.getMaxResponseBytes()));
                if (shouldRetry(snapshot, attempts, response.statusCode())) {
                    waitBeforeRetry(snapshot.retryDelay(), interceptors, request);
                    continue;
                }
                HttpResponse result = new HttpResponse(
                        response.statusCode(),
                        response.body(),
                        response.headers(),
                        Duration.ofNanos(System.nanoTime() - startedAt),
                        attempts);
                try {
                    for (HttpInterceptor interceptor : interceptors) {
                        interceptor.afterResponse(request, result);
                    }
                } catch (RuntimeException exception) {
                    HttpException wrapped = HttpException.interceptorFailed(exception);
                    notifyError(interceptors, request, wrapped);
                    throw wrapped;
                }
                return result;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                HttpException wrapped = HttpException.requestInterrupted(exception);
                notifyError(interceptors, request, wrapped);
                throw wrapped;
            } catch (IOException exception) {
                if (hasCause(exception, ResponseLimitExceededException.class)) {
                    HttpException wrapped = HttpException.responseTooLarge(exception);
                    notifyError(interceptors, request, wrapped);
                    throw wrapped;
                }
                if (shouldRetry(snapshot, attempts, null)) {
                    waitBeforeRetry(snapshot.retryDelay(), interceptors, request);
                    continue;
                }
                HttpException wrapped = exception instanceof HttpTimeoutException
                        ? HttpException.requestTimeout(exception)
                        : HttpException.requestFailed(exception);
                notifyError(interceptors, request, wrapped);
                throw wrapped;
            } catch (IllegalArgumentException exception) {
                HttpException wrapped = HttpException.invalidRequest(exception);
                notifyError(interceptors, request, wrapped);
                throw wrapped;
            }
        }
    }

    /**
     * 根据基础配置创建共享 JDK HTTP 客户端。
     *
     * @param config HTTP 基础配置
     * @return 线程安全 JDK HTTP 客户端
     */
    private static HttpClient buildClient(HttpConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .followRedirects(config.getRedirectPolicy())
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * 根据不可变快照构建一次可发送的 JDK 请求。
     *
     * @param snapshot 请求快照
     * @return JDK HTTP 请求
     * @throws IOException Multipart 文件请求体无法创建时抛出
     */
    private static java.net.http.HttpRequest buildRequest(HttpRequest.Snapshot snapshot) throws IOException {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(snapshot.uri())
                .timeout(snapshot.timeout());
        snapshot.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

        BodyPublisher bodyPublisher;
        if (snapshot.multipart()) {
            bodyPublisher = multipartBody(snapshot);
            builder.setHeader("Content-Type", "multipart/form-data; boundary=" + snapshot.multipartBoundary());
        } else if (snapshot.bodyBytes() != null) {
            bodyPublisher = BodyPublishers.ofByteArray(snapshot.bodyBytes());
        } else if (snapshot.body() != null) {
            bodyPublisher = BodyPublishers.ofString(snapshot.body(), snapshot.charset());
        } else {
            bodyPublisher = BodyPublishers.noBody();
        }
        return builder.method(snapshot.method().name(), bodyPublisher).build();
    }

    /**
     * 组合 Multipart 文本和文件 BodyPublisher，文件内容不会整体读入内存。
     *
     * @param snapshot 请求快照
     * @return 可重复订阅的 Multipart 请求体发布器
     * @throws IOException 文件媒体类型探测或发布器创建失败时抛出
     */
    private static BodyPublisher multipartBody(HttpRequest.Snapshot snapshot) throws IOException {
        List<BodyPublisher> publishers = new ArrayList<>();
        String boundary = snapshot.multipartBoundary();
        for (HttpRequest.FormField field : snapshot.formFields()) {
            String prefix = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + escapeQuoted(field.name()) + "\"\r\n"
                    + "Content-Type: text/plain; charset=" + snapshot.charset().name() + "\r\n\r\n";
            publishers.add(BodyPublishers.ofString(prefix + field.value() + "\r\n", snapshot.charset()));
        }
        for (HttpRequest.FormFile file : snapshot.formFiles()) {
            String fileName = file.path().getFileName().toString();
            String mediaType = Files.probeContentType(file.path());
            if (mediaType == null || mediaType.isBlank()) {
                mediaType = "application/octet-stream";
            }
            String prefix = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + escapeQuoted(file.name())
                    + "\"; filename=\"" + escapeQuoted(fileName) + "\"\r\n"
                    + "Content-Type: " + mediaType + "\r\n\r\n";
            publishers.add(BodyPublishers.ofString(prefix, StandardCharsets.UTF_8));
            publishers.add(BodyPublishers.ofFile(file.path()));
            publishers.add(BodyPublishers.ofByteArray("\r\n".getBytes(StandardCharsets.US_ASCII)));
        }
        publishers.add(BodyPublishers.ofString("--" + boundary + "--\r\n", StandardCharsets.US_ASCII));
        return BodyPublishers.concat(publishers.toArray(BodyPublisher[]::new));
    }

    /**
     * 转义 Content-Disposition 双引号参数值。
     *
     * @param value 原始字段名称或文件名
     * @return 可安全写入双引号参数的值
     */
    private static String escapeQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 判断当前响应或传输失败是否允许再次尝试。
     *
     * @param snapshot 请求快照
     * @param attempts 已完成尝试次数
     * @param statusCode HTTP 状态码；传输失败时为 {@code null}
     * @return 可以重试时返回 {@code true}
     */
    private static boolean shouldRetry(HttpRequest.Snapshot snapshot, int attempts, Integer statusCode) {
        if (attempts > snapshot.maxRetries() || !isRetryableMethod(snapshot)) {
            return false;
        }
        return statusCode == null || snapshot.retryOnStatus().contains(statusCode);
    }

    /**
     * 判断请求方法是否可安全自动重试。
     *
     * @param snapshot 请求快照
     * @return 幂等方法或调用方显式允许时返回 {@code true}
     */
    private static boolean isRetryableMethod(HttpRequest.Snapshot snapshot) {
        if (snapshot.retryNonIdempotent()) {
            return true;
        }
        return switch (snapshot.method()) {
            case GET, HEAD, PUT, DELETE, OPTIONS -> true;
            case POST, PATCH, TRACE -> false;
        };
    }

    /**
     * 在重试前等待固定时间，中断时恢复线程标记并抛出统一异常。
     *
     * @param delay 重试等待时间
     * @param interceptors 拦截器快照
     * @param request 原始请求构建器
     */
    private static void waitBeforeRetry(Duration delay,
                                        List<HttpInterceptor> interceptors,
                                        HttpRequest request) {
        if (delay.isZero()) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(delay.toNanos());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            HttpException wrapped = HttpException.requestInterrupted(exception);
            notifyError(interceptors, request, wrapped);
            throw wrapped;
        }
    }

    /**
     * 通知所有拦截器最终失败；拦截器通知异常作为抑制异常保留，不覆盖主失败。
     *
     * @param interceptors 拦截器快照
     * @param request 原始请求构建器
     * @param failure 最终失败
     */
    private static void notifyError(List<HttpInterceptor> interceptors,
                                    HttpRequest request,
                                    HttpException failure) {
        for (HttpInterceptor interceptor : interceptors) {
            try {
                interceptor.onError(request, failure);
            } catch (RuntimeException interceptorFailure) {
                failure.addSuppressed(interceptorFailure);
            }
        }
    }

    /**
     * 判断异常原因链是否包含指定类型。
     *
     * @param throwable 异常原因链起点
     * @param type 目标异常类型
     * @return 原因链包含目标类型时返回 {@code true}
     */
    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 创建限制响应体大小的处理器。
     *
     * @param maxBytes 最大响应体字节数
     * @return 有界响应体处理器
     */
    private static BodyHandler<byte[]> limitedBodyHandler(long maxBytes) {
        return ignored -> new LimitedBodySubscriber(maxBytes);
    }

    /**
     * 在订阅过程中限制内存响应体大小的字节订阅器。
     */
    private static final class LimitedBodySubscriber implements BodySubscriber<byte[]> {

        /** 最大允许字节数。 */
        private final long maxBytes;

        /** 已接收响应体缓冲区。 */
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        /** 响应体完成结果。 */
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();

        /** 上游订阅。 */
        private Flow.Subscription subscription;

        /** 已接收字节数。 */
        private long receivedBytes;

        /**
         * 创建有界响应订阅器。
         *
         * @param maxBytes 最大允许字节数
         */
        private LimitedBodySubscriber(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        /**
         * 获取异步响应体结果。
         *
         * @return 响应体完成阶段
         */
        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        /**
         * 保存上游订阅并请求第一批数据。
         *
         * @param subscription 上游响应体订阅
         */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        /**
         * 接收一批响应体缓冲区，并在越界时立即取消订阅。
         *
         * @param items 响应体缓冲区
         */
        @Override
        public void onNext(List<ByteBuffer> items) {
            long batchBytes = items.stream().mapToLong(ByteBuffer::remaining).sum();
            if (receivedBytes + batchBytes > maxBytes) {
                subscription.cancel();
                result.completeExceptionally(new ResponseLimitExceededException());
                return;
            }
            for (ByteBuffer item : items) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }
            receivedBytes += batchBytes;
            subscription.request(1);
        }

        /**
         * 将上游读取失败传递给调用方。
         *
         * @param throwable 上游失败
         */
        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        /**
         * 完成响应体读取并返回字节数组。
         */
        @Override
        public void onComplete() {
            result.complete(output.toByteArray());
        }
    }

    /**
     * 标记响应订阅因超过配置字节上限而取消。
     */
    private static final class ResponseLimitExceededException extends IOException {

        /** 序列化版本号。 */
        private static final long serialVersionUID = 1L;

        /** 创建不包含响应内容的越界标记异常。 */
        private ResponseLimitExceededException() {
            super("HTTP response body exceeded configured limit");
        }
    }
}
