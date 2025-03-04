package com.github.leyland.letool.tool.helper;

import com.github.leyland.letool.tool.configuration.SpringUtil;
import com.github.leyland.letool.tool.properties.HttpProperties;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @ClassName <h2>RestTemplateHelper</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
@ConditionalOnProperty(value = "spring.letool.http.enabled", havingValue = "true")
@ConditionalOnBean(RestTemplate.class)
@Component
public class RestTemplateHelper implements SmartInitializingSingleton {

    private final Logger log = LoggerFactory.getLogger(RestTemplateHelper.class);


    private final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();

    private RestTemplate restTemplate;

    @Resource(name = "httpClient")
    private CloseableHttpClient httpClient;


    @Override
    public void afterSingletonsInstantiated() {
        Map<String, ClientHttpRequestInterceptor> beansOfType = SpringUtil.getBeansOfType(ClientHttpRequestInterceptor.class);
        beansOfType.values().forEach(interceptor -> {
            log.debug("Adding interceptor: {}", interceptor.getClass().getName());
            interceptors.add(interceptor);
        });
        this.restTemplate = SpringUtil.getBean(RestTemplate.class);
        this.restTemplate.setInterceptors(interceptors);
    }

    /*@Autowired
    @Qualifier("restTemplate")
    public void setRestTemplate(RestTemplate restTemplate) {
        restTemplate.setInterceptors(interceptors);
        this.restTemplate = restTemplate;
    }*/

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }


    /**
     * 从工厂中New出一个 RestTemplate ，可设置超时时间。
     *
     * @param httpProperties
     */
    public RestTemplate buildRestTemplate(HttpProperties httpProperties) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(httpProperties.getConnectTimeout());
        requestFactory.setReadTimeout(httpProperties.getReadTimeout());

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }



    public <T> T sendRequest(HttpEntity<?> httpEntity, Class<T> responseType) {
        httpEntity.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<T> response = restTemplate.exchange((RequestEntity<?>) httpEntity, responseType);
        return response.getBody();
    }

    public <T> T sendRequest(HttpEntity<?> httpEntity, Class<T> responseType, HttpProperties httpProperties) {
        httpEntity.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<T> response = buildRestTemplate(httpProperties).exchange((RequestEntity<?>) httpEntity, responseType);
        return response.getBody();
    }


    /**
     * 发送 HTTP 请求，支持动态配置请求参数
     */
    public <T> T sendRequest(String url, HttpMethod method, Object requestBody, Class<T> responseType, Consumer<HttpHeaders> headerModifier) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headerModifier.accept(headers);

        HttpEntity<Object> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<T> response = restTemplate.exchange(url, method, requestEntity, responseType);

        return response.getBody();
    }

    public <T> T sendRequest(String url, Object requestBody, Class<T> responseType, Consumer<HttpHeaders> headerModifier) {
        return sendRequest(url, HttpMethod.POST, requestBody, responseType, headerModifier);
    }

}
