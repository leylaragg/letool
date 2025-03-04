package com.github.leyland.letool.tool;

import com.github.leyland.letool.tool.helper.RestTemplateHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.io.*;
import java.util.ArrayList;

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

        ResponseEntity<String> exchange = restTemplateHelper.getRestTemplate().exchange("http://localhost:9080/dms4/ccb/job", HttpMethod.POST, requestEntity, String.class);
        System.out.println(exchange.getBody());
        System.out.println(exchange.getStatusCode());
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
}
