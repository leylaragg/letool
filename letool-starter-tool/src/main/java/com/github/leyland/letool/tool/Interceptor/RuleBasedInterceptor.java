package com.github.leyland.letool.tool.Interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.core.util.IOUtils;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @ClassName <h2>RuleBasedInterceptor</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
@Component
@AutoConfigureAfter(RestTemplate.class)
public class RuleBasedInterceptor implements ClientHttpRequestInterceptor {

    private final Logger log = LoggerFactory.getLogger(RuleBasedInterceptor.class);

    private final RestTemplateInterceptorConfig config;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public final String EMAIL_TITLE = "X-Email-Title";

    public RuleBasedInterceptor(RestTemplateInterceptorConfig config) {
        this.config = config;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI uri = request.getURI();
        String host = uri.getHost();   // 获取域名
        String path = uri.getPath();   // 获取路径

        // 遍历所有规则，找到匹配的 Host
        for (InterceptorRule rule : config.getRules()) {
            if (rule.getHost().equalsIgnoreCase(host)) {
                // 如果 URI 在 excludedPaths 里面，则不拦截
                if (matchesAny(path, rule.getExcludedPaths())) {
                    return execution.execute(request, body);
                }

                // 如果 URI 在 includedPaths 里面，则拦截
                if (matchesAny(path, rule.getIncludedPaths())) {
                    log.debug("Applying interceptor to: {}", uri);

                    // 解析请求头中的邮件标题等信息
                    HttpHeaders headers = request.getHeaders();
                    List<String> strings = headers.get(EMAIL_TITLE);

                    // 将报文指定节点序列化
                    ClientHttpResponse response = execution.execute(request, body);
                    CustomClientHttpResponseWrapper bufferResponse = new CustomClientHttpResponseWrapper(response);

                    HttpStatus statusCode = bufferResponse.getStatusCode();
                    Boolean resStatus;
                    String msg = "";

                    StringBuilder responseBody = new StringBuilder();
                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(bufferResponse.getBody(), StandardCharsets.UTF_8))) {
                        char[] charBuffer = new char[1024];
                        int bytesRead;
                        while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                            responseBody.append(charBuffer, 0, bytesRead);
                        }
                    }

                    log.debug("响应报文{}", responseBody);


                    try {
                        String resBody = responseBody.toString();
                        if (!StringUtils.isBlank(resBody)) {
                            JSONObject jsonObject = JSON.parseObject(resBody);
                            // 解析数据
                            try {
                                resStatus = jsonObject.getBoolean("success");
                                if (!resStatus || jsonObject.getIntValue("code") != 200) {
                                    msg = jsonObject.getString("msg");
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage());
                                msg = e.toString();
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        msg = e.toString();
                    } finally {
                        //发送邮件
                        if (!StringUtils.isBlank(msg)) {
                            //...
                        }
                    }

                    return bufferResponse;

                }
            }
        }

        return execution.execute(request, body);
    }

    /**
     * 判断请求 URI 是否匹配配置规则
     */
    private boolean matchesAny(String requestUri, List<String> patterns) {
        if (patterns == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }
}

