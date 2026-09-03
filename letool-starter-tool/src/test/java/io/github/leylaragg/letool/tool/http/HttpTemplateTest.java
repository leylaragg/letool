package io.github.leylaragg.letool.tool.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 HTTP 模板在拦截器失败和线程中断时保留既有异常契约。
 */
class HttpTemplateTest {

    /**
     * 清理测试线程的中断标记，避免影响同一线程执行的后续用例。
     */
    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    /**
     * 前置拦截器失败时应包装主异常，并把错误通知失败保留为 suppressed。
     *
     * @throws Exception Mockito 校验 JDK 客户端受检异常签名
     */
    @Test
    void beforeInterceptorFailureShouldPreserveCauseAndSuppressedFailure() throws Exception {
        HttpClient client = mock(HttpClient.class);
        RuntimeException interceptorFailure = new IllegalStateException("before failed");
        RuntimeException notificationFailure = new IllegalArgumentException("notify failed");
        AtomicReference<Exception> notifiedFailure = new AtomicReference<>();
        HttpInterceptor failingInterceptor = new HttpInterceptor() {
            @Override
            public void beforeRequest(HttpRequest request) {
                throw interceptorFailure;
            }
        };
        HttpInterceptor notificationInterceptor = notificationInterceptor(
                notifiedFailure,
                notificationFailure);

        assertThatThrownBy(() -> new HttpTemplate(HttpConfig.defaults(), client)
                .create("http://localhost/test")
                .get()
                .interceptor(failingInterceptor)
                .interceptor(notificationInterceptor)
                .execute())
                .isInstanceOfSatisfying(HttpException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.INTERCEPTOR_FAILED.getCode());
                    assertThat(exception.getCause()).isSameAs(interceptorFailure);
                    assertThat(exception.getSuppressed()).containsExactly(notificationFailure);
                    assertThat(notifiedFailure.get()).isSameAs(exception);
                });
        verify(client, never()).<byte[]>send(any(), any());
    }

    /**
     * 后置拦截器失败时应使用与前置拦截器相同的包装和通知规则。
     *
     * @throws Exception Mockito 模拟 JDK 客户端受检异常签名
     */
    @Test
    void afterInterceptorFailureShouldUseSameErrorContract() throws Exception {
        HttpClient client = mock(HttpClient.class);
        java.net.http.HttpResponse<byte[]> response = response(200);
        when(client.<byte[]>send(any(), any())).thenReturn(response);
        RuntimeException interceptorFailure = new IllegalStateException("after failed");
        AtomicReference<Exception> notifiedFailure = new AtomicReference<>();
        HttpInterceptor failingInterceptor = new HttpInterceptor() {
            @Override
            public void afterResponse(HttpRequest request, HttpResponse actualResponse) {
                throw interceptorFailure;
            }
        };
        HttpInterceptor notificationInterceptor = notificationInterceptor(notifiedFailure, null);

        assertThatThrownBy(() -> new HttpTemplate(HttpConfig.defaults(), client)
                .create("http://localhost/test")
                .get()
                .interceptor(failingInterceptor)
                .interceptor(notificationInterceptor)
                .execute())
                .isInstanceOfSatisfying(HttpException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.INTERCEPTOR_FAILED.getCode());
                    assertThat(exception.getCause()).isSameAs(interceptorFailure);
                    assertThat(exception.getSuppressed()).isEmpty();
                    assertThat(notifiedFailure.get()).isSameAs(exception);
                });
        verify(client).<byte[]>send(any(), any());
    }

    /**
     * 发送阶段被中断时应恢复线程标记，并把统一异常通知给拦截器。
     *
     * @throws Exception Mockito 模拟 JDK 客户端受检异常签名
     */
    @Test
    void sendInterruptionShouldRestoreFlagAndNotifyFailure() throws Exception {
        HttpClient client = mock(HttpClient.class);
        InterruptedException interruption = new InterruptedException("send interrupted");
        when(client.<byte[]>send(any(), any())).thenThrow(interruption);
        AtomicReference<Exception> notifiedFailure = new AtomicReference<>();

        assertThatThrownBy(() -> new HttpTemplate(HttpConfig.defaults(), client)
                .create("http://localhost/test")
                .get()
                .interceptor(notificationInterceptor(notifiedFailure, null))
                .execute())
                .isInstanceOfSatisfying(HttpException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.REQUEST_INTERRUPTED.getCode());
                    assertThat(exception.getCause()).isSameAs(interruption);
                    assertThat(notifiedFailure.get()).isSameAs(exception);
                    assertThat(Thread.currentThread().isInterrupted()).isTrue();
                });
    }

    /**
     * 重试等待被中断时应停止后续发送，并复用发送中断的异常契约。
     *
     * @throws Exception Mockito 模拟 JDK 客户端受检异常签名
     */
    @Test
    void retryDelayInterruptionShouldStopRetriesAndUseSameErrorContract() throws Exception {
        HttpClient client = mock(HttpClient.class);
        java.net.http.HttpResponse<byte[]> response = response(503);
        when(client.<byte[]>send(any(), any())).thenReturn(response);
        AtomicReference<Exception> notifiedFailure = new AtomicReference<>();
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> new HttpTemplate(HttpConfig.defaults(), client)
                .create("http://localhost/test")
                .get()
                .maxRetry(1)
                .retryOn(503)
                .retryDelay(Duration.ofMillis(10))
                .interceptor(notificationInterceptor(notifiedFailure, null))
                .execute())
                .isInstanceOfSatisfying(HttpException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(HttpErrorCode.REQUEST_INTERRUPTED.getCode());
                    assertThat(exception.getCause()).isInstanceOf(InterruptedException.class);
                    assertThat(notifiedFailure.get()).isSameAs(exception);
                    assertThat(Thread.currentThread().isInterrupted()).isTrue();
                });
        verify(client).<byte[]>send(any(), any());
    }

    /**
     * 创建记录错误通知、并可选模拟通知失败的拦截器。
     *
     * @param notifiedFailure 接收主异常的引用
     * @param notificationFailure 需要模拟的通知异常；为 {@code null} 时正常返回
     * @return 错误通知拦截器
     */
    private static HttpInterceptor notificationInterceptor(
            AtomicReference<Exception> notifiedFailure,
            RuntimeException notificationFailure) {
        return new HttpInterceptor() {
            @Override
            public void onError(HttpRequest request, Exception exception) {
                notifiedFailure.set(exception);
                if (notificationFailure != null) {
                    throw notificationFailure;
                }
            }
        };
    }

    /**
     * 创建供模板读取的最小 JDK 响应。
     *
     * @param statusCode HTTP 状态码
     * @return 带空响应体和响应头的模拟响应
     */
    @SuppressWarnings("unchecked")
    private static java.net.http.HttpResponse<byte[]> response(int statusCode) {
        java.net.http.HttpResponse<byte[]> response = mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(new byte[0]);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        return response;
    }
}
