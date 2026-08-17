package io.github.leylaragg.letool.security.config;

import io.github.leylaragg.letool.security.context.LoginUser;
import io.github.leylaragg.letool.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security Resource Server 真实过滤链集成测试。
 */
@SpringBootTest(
        classes = SecurityResourceServerIntegrationTest.TestApplication.class,
        properties = {
                "letool.security.jwt.secret=test-resource-server-secret-key-256-bits!!",
                "letool.security.jwt.issuer=resource-server-test",
                "letool.security.exclude-paths=/public/{*path}"
        }
)
@AutoConfigureMockMvc
class SecurityResourceServerIntegrationTest {

    /** MockMvc 请求入口。 */
    @Autowired
    private MockMvc mockMvc;

    /** 用于生成与资源服务器配置一致的测试令牌。 */
    @Autowired
    private JwtTokenProvider tokenProvider;

    /**
     * 验证访问令牌可以通过标准 Authorization Bearer 头完成认证。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void accessTokenShouldAuthenticateThroughBearerHeader() throws Exception {
        mockMvc.perform(get("/secured")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("secured"));
    }

    /**
     * 验证刷新令牌不能进入业务资源。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void refreshTokenShouldBeRejectedByResourceServer() throws Exception {
        mockMvc.perform(get("/secured")
                        .header("Authorization", "Bearer " + refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_002"));
    }

    /**
     * 验证查询参数中的 token 不再被接受，避免令牌进入 URL、日志和浏览器历史。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void queryParameterTokenShouldNotAuthenticate() throws Exception {
        mockMvc.perform(get("/secured").queryParam("token", accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_002"));
    }

    /**
     * 验证 permissions Claim 会映射为 Spring Security 原生 authority。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void permissionClaimShouldSupportPreAuthorize() throws Exception {
        mockMvc.perform(get("/permission")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("permission"));
    }

    /**
     * 验证 roles Claim 会按照 Spring Security 约定映射为 ROLE_ authority。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void roleClaimShouldSupportPreAuthorize() throws Exception {
        mockMvc.perform(get("/role")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("role"));
    }

    /**
     * 验证已认证但权限不足时返回统一的安全错误码。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void missingPermissionShouldReturnUnifiedAccessDeniedCode()
            throws Exception {
        LoginUser user = new LoginUser(
                2L,
                "reader",
                List.of("USER"),
                List.of()
        );
        String token = tokenProvider.generateAccessToken(user);

        mockMvc.perform(get("/permission")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECURITY_003"));
    }

    /**
     * 验证配置排除路径可以匿名访问。
     *
     * @throws Exception 当测试请求执行失败时抛出
     */
    @Test
    void configuredExcludedPathShouldRemainPublic() throws Exception {
        mockMvc.perform(get("/public/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("UP"));
    }

    /**
     * 生成具有角色和权限的访问令牌。
     *
     * @return 访问令牌
     */
    private String accessToken() {
        return tokenProvider.generateAccessToken(loginUser());
    }

    /**
     * 生成与访问令牌主体相同的刷新令牌。
     *
     * @return 刷新令牌
     */
    private String refreshToken() {
        return tokenProvider.generateRefreshToken(loginUser());
    }

    /**
     * 创建测试登录用户。
     *
     * @return 具有 ADMIN 角色和读取权限的用户
     */
    private LoginUser loginUser() {
        return new LoginUser(
                1L,
                "admin",
                List.of("ADMIN"),
                List.of("user:read")
        );
    }

    /**
     * 测试应用，仅加载自动配置和测试控制器。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(SecurityProbeController.class)
    static class TestApplication {
    }

    /**
     * 资源服务器行为探针。
     */
    @RestController
    static class SecurityProbeController {

        /**
         * 需要认证的普通资源。
         *
         * @return 固定响应
         */
        @GetMapping("/secured")
        String secured() {
            return "secured";
        }

        /**
         * 需要 user:read 权限的资源。
         *
         * @return 固定响应
         */
        @PreAuthorize("hasAuthority('user:read')")
        @GetMapping("/permission")
        String permission() {
            return "permission";
        }

        /**
         * 需要 ADMIN 角色的资源。
         *
         * @return 固定响应
         */
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/role")
        String role() {
            return "role";
        }

        /**
         * 由 exclude-paths 显式公开的资源。
         *
         * @return 健康状态
         */
        @GetMapping("/public/health")
        String health() {
            return "UP";
        }
    }
}
