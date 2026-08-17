package io.github.leylaragg.letool.web.version;

import io.github.leylaragg.letool.web.config.WebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 主版本路由的 MVC 集成和条件语义测试。
 */
@SpringBootTest(classes = ApiVersionMvcIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class ApiVersionMvcIntegrationTest {

    /** MVC 测试客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证相同路径可以按请求头或查询参数路由到不同主版本。
     *
     * @throws Exception MVC 请求执行失败时抛出
     */
    @Test
    void shouldRouteSamePathByMajorVersion() throws Exception {
        mockMvc.perform(get("/versioned").header("X-API-Version", "1.7.2"))
                .andExpect(status().isOk())
                .andExpect(content().string("v1"));

        mockMvc.perform(get("/versioned").param("apiVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("v2"));
    }

    /**
     * 验证请求头一旦存在就拥有绝对优先级，非法请求头不会回退到查询参数。
     */
    @Test
    void shouldNotFallBackToParameterWhenHeaderExists() {
        ApiVersionRequestMapping condition = new ApiVersionRequestMapping(
                2,
                "X-Version",
                "version");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/versioned");
        request.addHeader("X-Version", "invalid");
        request.setParameter("version", "2");

        assertThat(condition.getMatchingCondition(request)).isNull();
    }

    /**
     * 验证不符合数字点分格式的版本不会参与路由匹配。
     *
     * @param value 非法版本文本
     */
    @ParameterizedTest
    @ValueSource(strings = {"v1", "1-beta", "1.", ".1", "1..2", "2147483648"})
    void shouldRejectInvalidVersion(String value) {
        ApiVersionRequestMapping condition = new ApiVersionRequestMapping(
                1,
                "X-API-Version",
                "apiVersion");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/versioned");
        request.addHeader("X-API-Version", value);

        assertThat(condition.getMatchingCondition(request)).isNull();
    }

    /**
     * 验证版本条件具备稳定相等性，使 Spring 能识别同版本重复映射。
     */
    @Test
    void shouldUseVersionAndSourceNamesForConditionIdentity() {
        ApiVersionRequestMapping first = new ApiVersionRequestMapping(1, "X-Version", "version");
        ApiVersionRequestMapping same = new ApiVersionRequestMapping(1, "X-Version", "version");
        ApiVersionRequestMapping other = new ApiVersionRequestMapping(2, "X-Version", "version");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
        assertThat(first.toString()).contains("version=1", "X-Version", "version");
    }

    /**
     * 验证注解声明的主版本必须为正整数。
     */
    @Test
    void shouldRejectNonPositiveVersion() {
        assertThatThrownBy(() -> new ApiVersionRequestMapping(0, "X-Version", "version"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    /**
     * 仅导入 Web 自动配置的最小测试应用。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ImportAutoConfiguration(WebAutoConfiguration.class)
    static class TestApplication {

        /**
         * 注册版本路由测试 Controller。
         *
         * @return 版本路由测试 Controller
         */
        @Bean
        VersionedController versionedController() {
            return new VersionedController();
        }
    }

    /**
     * 提供相同路径不同主版本处理方法的测试 Controller。
     */
    @RestController
    static class VersionedController {

        /**
         * 返回第一版结果。
         *
         * @return 第一版标识
         */
        @ApiVersion(1)
        @GetMapping("/versioned")
        String versionOne() {
            return "v1";
        }

        /**
         * 返回第二版结果。
         *
         * @return 第二版标识
         */
        @ApiVersion(2)
        @GetMapping("/versioned")
        String versionTwo() {
            return "v2";
        }
    }
}
