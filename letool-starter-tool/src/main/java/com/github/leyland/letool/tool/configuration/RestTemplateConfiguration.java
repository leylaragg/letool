package com.github.leyland.letool.tool.configuration;

import com.github.leyland.letool.tool.properties.HttpProperties;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @ClassName <h2>RestTemplateConfiguration</h2>
 * @Description
 * @Author rungo
 * @Date 2/27/2025
 * @Version 1.0
 **/
@Configuration
public class RestTemplateConfiguration {

    @Bean
    public HttpProperties httpProperties() {
        return new HttpProperties();
    }

    @Bean("httpClient")
    @ConditionalOnMissingBean(CloseableHttpClient.class)
    @ConditionalOnBean(HttpProperties.class)
    public CloseableHttpClient httpClient(@Autowired HttpProperties httpProperties) {
        // 连接池
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(httpProperties.getMaxTotalConnections());
        connectionManager.setDefaultMaxPerRoute(httpProperties.getMaxPerRouteConnections());

        // HttpClient
        return HttpClients.custom()
                .setConnectionManager(connectionManager).build();
    }

    @Bean("restTemplate")
    @Autowired
    @ConditionalOnMissingBean(RestTemplate.class)
    @ConditionalOnBean({CloseableHttpClient.class, HttpProperties.class})
    public RestTemplate restTemplate(@Qualifier("httpClient") CloseableHttpClient httpClient, HttpProperties httpProperties) {
        // 配置请求工厂
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(httpProperties.getConnectTimeout());
        requestFactory.setReadTimeout(httpProperties.getReadTimeout());

        return new RestTemplate(requestFactory);
    }


}
