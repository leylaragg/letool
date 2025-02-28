package com.github.leyland.letool.tool;

import com.github.leyland.letool.tool.helper.RestTemplateHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

/**
 * @ClassName <h2>com.github.leyland.letool.tool.SpringTest</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
@SpringBootTest
public class SpringTest {

    @Autowired
    private RestTemplateHelper restTemplateHelper;

    @Test
    public void test() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String jsonBody = "{\"name\": \"Alice\", \"age\": 25}";
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

        System.out.println(jsonBody.getBytes().length);

        ResponseEntity<String> exchange = restTemplateHelper.getRestTemplate().exchange("http://localhost:8080/", HttpMethod.POST, requestEntity, String.class);
        System.out.println(exchange.getBody());
        System.out.println(exchange.getStatusCode());
    }
}
