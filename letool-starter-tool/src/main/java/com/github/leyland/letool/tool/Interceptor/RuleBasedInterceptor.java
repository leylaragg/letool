package com.github.leyland.letool.tool.Interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.core.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

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
public class RuleBasedInterceptor implements ClientHttpRequestInterceptor {

    private final Logger log = LoggerFactory.getLogger(RuleBasedInterceptor.class);

    private final RestTemplateInterceptorConfig config;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

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
                    // 将报文指定节点序列化
                    try (ClientHttpResponse response = execution.execute(request, body)) {
                        /*JSONObject jsonObject = JSON.parseObject(response.getBody().readAllBytes());
                        log.debug("响应报文：{} ", JSON.toJSONString(jsonObject));*/
                        InputStream responseInSteam = response.getBody();
                        StringBuffer stringBuffer = new StringBuffer();
                        char[] buffer = new char[1024];
                        InputStreamReader inputStreamReader = new InputStreamReader(responseInSteam, StandardCharsets.UTF_8);

                        while (responseInSteam.available() > 0) {
                            if (inputStreamReader.read(buffer, 0 , responseInSteam.available()) > 0) {
                                stringBuffer.append(buffer);
                            }

                        }
                        System.out.println("响应报文！！！" + stringBuffer);
                        /**
                         * if(!= 200)  预警通知
                         */
                        return response;
                    }
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
