package com.github.leyland.letool.data.letool.tool;

import com.github.leyland.letool.data.letool.tool.Interceptor.RuleBasedInterceptor;
import com.github.leyland.letool.data.letool.tool.configuration.SpringUtil;
import com.github.leyland.letool.data.letool.tool.helper.RestTemplateHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        ArrayList<String> objects = new ArrayList<>();
        objects.add("Hello World");
        headers.put("X-Email-Title", objects);

        String jsonBody = "{\"name\": \"Alice\", \"age\": 25}";
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

        System.out.println(jsonBody.getBytes().length);

        // 如果上加上 @ConditionalOnBean(RestTemplate.class) 则此Bean不会创建，为什么
        RuleBasedInterceptor bean = SpringUtil.getBean(RuleBasedInterceptor.class);

//        ResponseEntity<String> exchange = restTemplateHelper.getRestTemplate().exchange("http://localhost:9080/dms4/ccb/job", HttpMethod.POST, requestEntity, String.class);
//        System.out.println(exchange.getBody());
//        System.out.println(exchange.getStatusCode());
        RequestEntity<String> requestEntity1 = RequestEntity.post(URI.create("http://localhost:8080/dms4/ccb/job")).headers(headers).body(jsonBody, String.class);
        String resBody = restTemplateHelper.sendRequest(requestEntity1, String.class);
        System.out.println(resBody);
    }

    @Test
    public void test2() throws IOException {
        byte[] bytes = "Hello World                  ".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        char[] charBuffer = new char[1024];
        StringBuilder stringBuilder = new StringBuilder();
        int bytesRead;
        while (( bytesRead = bufferedReader.read(charBuffer)) > 0 ) {
            stringBuilder.append(charBuffer, 0, bytesRead);
        }

        System.out.println(stringBuilder);

        //        bufferedReader.lines().forEach(System.out::println);

    }

    @Test
    public void test3() throws IOException {
        byte[] bytes = "Hello World                  ".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        StringBuffer stringBuffer = new StringBuffer();
        StringBuilder stringBuilder = new StringBuilder();
        char[] buffer = new char[1024];
        int bytesRead;
        while (inputStream.available() > 0) {
            if ((bytesRead = inputStreamReader.read(buffer, 0 , inputStream.available())) > 0) {
                stringBuffer.append(buffer);
                stringBuilder.append(buffer, 0, bytesRead);
            }

        }

        System.out.println(stringBuffer);
        System.out.println(stringBuilder);
    }

    @Test
    public void test4() throws IOException {
        System.out.println(Instant.now().toEpochMilli());

        long epochMilli = Instant.now().toEpochMilli();
        long newTimeStamp = epochMilli + TimeUnit.MINUTES.toMillis(20);
        System.out.println(newTimeStamp);
    }

    @Test
    public void test5() throws IOException {
        System.out.println(SpringBeanAutowiringSupport.class.getClassLoader());
    }
}
