package com.github.leyland.letool.swagger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SwaggerProperties Swagger 配置属性测试")
class SwaggerPropertiesTest {

    @Nested
    @DisplayName("基础属性默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("enabled 默认应为 true")
        void enabledShouldDefaultToTrue() {
            SwaggerProperties props = new SwaggerProperties();
            assertTrue(props.isEnabled());
        }

        @Test
        @DisplayName("title 应有默认值")
        void titleShouldHaveDefault() {
            SwaggerProperties props = new SwaggerProperties();
            assertNotNull(props.getTitle());
            assertEquals("API Documentation", props.getTitle());
        }

        @Test
        @DisplayName("description 默认应为空字符串")
        void descriptionShouldDefaultToEmpty() {
            SwaggerProperties props = new SwaggerProperties();
            assertEquals("", props.getDescription());
        }

        @Test
        @DisplayName("version 默认应为 '1.0.0'")
        void versionShouldDefaultTo100() {
            SwaggerProperties props = new SwaggerProperties();
            assertEquals("1.0.0", props.getVersion());
        }
    }

    @Nested
    @DisplayName("基础属性 getter/setter 测试")
    class BasicSetterGetterTests {

        @Test
        @DisplayName("enabled 应正确存取")
        void enabledGetterSetter() {
            SwaggerProperties props = new SwaggerProperties();
            props.setEnabled(false);
            assertFalse(props.isEnabled());
        }

        @Test
        @DisplayName("title 应正确存取")
        void titleGetterSetter() {
            SwaggerProperties props = new SwaggerProperties();
            props.setTitle("My API");
            assertEquals("My API", props.getTitle());
        }

        @Test
        @DisplayName("description 应正确存取")
        void descriptionGetterSetter() {
            SwaggerProperties props = new SwaggerProperties();
            props.setDescription("RESTful API 文档");
            assertEquals("RESTful API 文档", props.getDescription());
        }

        @Test
        @DisplayName("version 应正确存取")
        void versionGetterSetter() {
            SwaggerProperties props = new SwaggerProperties();
            props.setVersion("2.0.0");
            assertEquals("2.0.0", props.getVersion());
        }
    }

    @Nested
    @DisplayName("Contact 联系人配置测试")
    class ContactTests {

        @Test
        @DisplayName("contact 默认非 null")
        void contactShouldNotBeNull() {
            SwaggerProperties props = new SwaggerProperties();
            assertNotNull(props.getContact());
        }

        @Test
        @DisplayName("name 应正确存取")
        void nameGetterSetter() {
            SwaggerProperties.Contact contact = new SwaggerProperties.Contact();
            contact.setName("张三");
            assertEquals("张三", contact.getName());
        }

        @Test
        @DisplayName("email 应正确存取")
        void emailGetterSetter() {
            SwaggerProperties.Contact contact = new SwaggerProperties.Contact();
            contact.setEmail("zhangsan@example.com");
            assertEquals("zhangsan@example.com", contact.getEmail());
        }

        @Test
        @DisplayName("url 应正确存取")
        void urlGetterSetter() {
            SwaggerProperties.Contact contact = new SwaggerProperties.Contact();
            contact.setUrl("https://example.com");
            assertEquals("https://example.com", contact.getUrl());
        }

        @Test
        @DisplayName("setContact 应正确替换实例")
        void setContactShouldReplace() {
            SwaggerProperties props = new SwaggerProperties();
            SwaggerProperties.Contact custom = new SwaggerProperties.Contact();
            custom.setName("李四");
            props.setContact(custom);
            assertEquals("李四", props.getContact().getName());
        }
    }

    @Nested
    @DisplayName("Security 安全认证配置测试")
    class SecurityTests {

        @Test
        @DisplayName("security 默认非 null")
        void securityShouldNotBeNull() {
            SwaggerProperties props = new SwaggerProperties();
            assertNotNull(props.getSecurity());
        }

        @Test
        @DisplayName("bearerToken 默认应为 true")
        void bearerTokenShouldDefaultToTrue() {
            SwaggerProperties.Security security = new SwaggerProperties.Security();
            assertTrue(security.isBearerToken());
        }

        @Test
        @DisplayName("headerName 默认应为 'Authorization'")
        void headerNameShouldDefaultToAuthorization() {
            SwaggerProperties.Security security = new SwaggerProperties.Security();
            assertEquals("Authorization", security.getHeaderName());
        }

        @Test
        @DisplayName("bearerToken 应正确存取")
        void bearerTokenGetterSetter() {
            SwaggerProperties.Security security = new SwaggerProperties.Security();
            security.setBearerToken(false);
            assertFalse(security.isBearerToken());
        }

        @Test
        @DisplayName("headerName 应正确存取")
        void headerNameGetterSetter() {
            SwaggerProperties.Security security = new SwaggerProperties.Security();
            security.setHeaderName("X-Auth-Token");
            assertEquals("X-Auth-Token", security.getHeaderName());
        }

        @Test
        @DisplayName("setSecurity 应正确替换实例")
        void setSecurityShouldReplace() {
            SwaggerProperties props = new SwaggerProperties();
            SwaggerProperties.Security custom = new SwaggerProperties.Security();
            custom.setBearerToken(false);
            props.setSecurity(custom);
            assertFalse(props.getSecurity().isBearerToken());
        }
    }

    @Nested
    @DisplayName("Knife4j 增强配置测试")
    class Knife4jTests {

        @Test
        @DisplayName("knife4j 默认非 null")
        void knife4jShouldNotBeNull() {
            SwaggerProperties props = new SwaggerProperties();
            assertNotNull(props.getKnife4j());
        }

        @Test
        @DisplayName("offlineDocs 默认应为 false")
        void offlineDocsShouldDefaultToFalse() {
            SwaggerProperties.Knife4j knife4j = new SwaggerProperties.Knife4j();
            assertFalse(knife4j.isOfflineDocs());
        }

        @Test
        @DisplayName("enableFooter 默认应为 false")
        void enableFooterShouldDefaultToFalse() {
            SwaggerProperties.Knife4j knife4j = new SwaggerProperties.Knife4j();
            assertFalse(knife4j.isEnableFooter());
        }

        @Test
        @DisplayName("offlineDocs 应正确存取")
        void offlineDocsGetterSetter() {
            SwaggerProperties.Knife4j knife4j = new SwaggerProperties.Knife4j();
            knife4j.setOfflineDocs(true);
            assertTrue(knife4j.isOfflineDocs());
        }

        @Test
        @DisplayName("enableFooter 应正确存取")
        void enableFooterGetterSetter() {
            SwaggerProperties.Knife4j knife4j = new SwaggerProperties.Knife4j();
            knife4j.setEnableFooter(true);
            assertTrue(knife4j.isEnableFooter());
        }

        @Test
        @DisplayName("setKnife4j 应正确替换实例")
        void setKnife4jShouldReplace() {
            SwaggerProperties props = new SwaggerProperties();
            SwaggerProperties.Knife4j custom = new SwaggerProperties.Knife4j();
            custom.setOfflineDocs(true);
            props.setKnife4j(custom);
            assertTrue(props.getKnife4j().isOfflineDocs());
        }
    }

    @Nested
    @DisplayName("Group API 分组配置测试")
    class GroupTests {

        @Test
        @DisplayName("groups 默认应为空列表")
        void groupsShouldDefaultToEmpty() {
            SwaggerProperties props = new SwaggerProperties();
            assertNotNull(props.getGroups());
            assertTrue(props.getGroups().isEmpty());
        }

        @Test
        @DisplayName("name 应正确存取")
        void nameGetterSetter() {
            SwaggerProperties.Group group = new SwaggerProperties.Group();
            group.setName("用户模块");
            assertEquals("用户模块", group.getName());
        }

        @Test
        @DisplayName("basePackage 应正确存取")
        void basePackageGetterSetter() {
            SwaggerProperties.Group group = new SwaggerProperties.Group();
            group.setBasePackage("com.example.user.controller");
            assertEquals("com.example.user.controller", group.getBasePackage());
        }

        @Test
        @DisplayName("setGroups 应正确替换列表")
        void setGroupsShouldReplace() {
            SwaggerProperties props = new SwaggerProperties();
            SwaggerProperties.Group g1 = new SwaggerProperties.Group();
            g1.setName("用户模块");
            g1.setBasePackage("com.example.user");
            SwaggerProperties.Group g2 = new SwaggerProperties.Group();
            g2.setName("订单模块");
            g2.setBasePackage("com.example.order");
            props.setGroups(List.of(g1, g2));

            assertEquals(2, props.getGroups().size());
            assertEquals("用户模块", props.getGroups().get(0).getName());
            assertEquals("订单模块", props.getGroups().get(1).getName());
        }
    }
}
