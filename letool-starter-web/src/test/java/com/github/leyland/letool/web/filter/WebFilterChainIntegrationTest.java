package com.github.leyland.letool.web.filter;

import com.github.leyland.letool.web.config.WebAutoConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runtime filter-chain tests for the web starter.
 */
@SpringBootTest(classes = WebFilterChainIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class WebFilterChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies XSS escaping happens through the registered Servlet filter chain.
     */
    @Test
    void shouldEscapeRequestParametersThroughXssFilter() throws Exception {
        mockMvc.perform(get("/echo").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    /**
     * Verifies suspicious SQL parameters are rejected before reaching MVC controllers.
     */
    @Test
    void shouldRejectSqlInjectionParametersThroughFilterChain() throws Exception {
        mockMvc.perform(get("/echo").param("q", "SELECT * FROM users"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies suspicious SQL payloads in request bodies are rejected before MVC controllers.
     */
    @Test
    void shouldRejectSqlInjectionBodyThroughFilterChain() throws Exception {
        mockMvc.perform(post("/body-twice")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("DROP TABLE users"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies request bodies remain readable after another filter consumes the body first.
     */
    @Test
    void shouldAllowRequestBodyToBeReadMoreThanOnce() throws Exception {
        mockMvc.perform(post("/body-twice")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello-body"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello-body|hello-body"));
    }

    /**
     * Minimal test application importing only the web auto configuration under test.
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ImportAutoConfiguration(WebAutoConfiguration.class)
    static class TestApplication {

        @Bean
        EchoController echoController() {
            return new EchoController();
        }

        @Bean
        FilterRegistrationBean<BodyReadProbeFilter> bodyReadProbeFilterRegistration() {
            FilterRegistrationBean<BodyReadProbeFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new BodyReadProbeFilter());
            registration.addUrlPatterns("/*");
            registration.setOrder(-104);
            registration.setName("bodyReadProbeFilter");
            return registration;
        }
    }

    /**
     * Controller used to observe the request after web filters have run.
     */
    @RestController
    static class EchoController {

        @GetMapping("/echo")
        String echo(@RequestParam String q) {
            return q;
        }

        @PostMapping("/body-twice")
        String bodyTwice(@RequestBody String body, HttpServletRequest request) {
            return request.getAttribute("probeBody") + "|" + body;
        }
    }

    /**
     * Test filter that consumes the request body before MVC sees it.
     */
    static class BodyReadProbeFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            request.setAttribute("probeBody", body);
            chain.doFilter(request, response);
        }
    }
}
