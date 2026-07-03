package com.github.leyland.letool.web.version;

import com.github.leyland.letool.web.config.WebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring MVC integration tests for API version routing.
 */
@SpringBootTest(
        classes = ApiVersionMvcIntegrationTest.TestApplication.class,
        properties = {
                "letool.web.xss-filter.enabled=false",
                "letool.web.sql-injection-filter.enabled=false"
        })
@AutoConfigureMockMvc
class ApiVersionMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that identical paths can route to different handlers by API version.
     */
    @Test
    void shouldRouteSamePathByApiVersion() throws Exception {
        mockMvc.perform(get("/versioned").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("v1"));

        mockMvc.perform(get("/versioned").param("apiVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("v2"));
    }

    /**
     * Verifies invalid version parameters are treated as no-match instead of throwing.
     */
    @Test
    void shouldIgnoreInvalidApiVersionParameter() {
        ApiVersionRequestMapping condition = new ApiVersionRequestMapping(1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/versioned");
        request.setParameter("apiVersion", "latest");

        assertThatCode(() -> condition.getMatchingCondition(request)).doesNotThrowAnyException();
        assertThat(condition.getMatchingCondition(request)).isNull();
    }

    /**
     * Minimal test application importing only the web auto configuration under test.
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ImportAutoConfiguration(WebAutoConfiguration.class)
    static class TestApplication {

        @Bean
        VersionedController versionedController() {
            return new VersionedController();
        }
    }

    /**
     * Controller with two handlers sharing the same path and HTTP method.
     */
    @RestController
    static class VersionedController {

        @ApiVersion(1)
        @GetMapping("/versioned")
        String versionOne() {
            return "v1";
        }

        @ApiVersion(2)
        @GetMapping("/versioned")
        String versionTwo() {
            return "v2";
        }
    }
}
