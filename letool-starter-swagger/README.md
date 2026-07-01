# letool-starter-swagger

## 模块简介

API 文档模块，基于 Knife4j 4.5.0 + SpringDoc OpenAPI 3.x，零代码自动配置即可生成在线 API 文档。支持文档基础信息定制、Bearer Token 安全认证、API 分组、离线文档导出、自定义页脚等增强功能。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-swagger</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 引入依赖后，在 `application.yml` 中配置：

```yaml
letool:
  swagger:
    enabled: true
    title: "项目接口文档"
    description: "RESTful API 接口文档"
    version: "1.0.0"
    contact:
      name: "开发团队"
      email: "dev@example.com"
    security:
      bearer-token: true
      header-name: "Authorization"
```

**Step 2** — 启动应用，访问文档页面：

```
Knife4j 增强 UI:  http://localhost:8080/doc.html
OpenAPI 3 JSON:   http://localhost:8080/v3/api-docs
```

至此，你的所有 Spring MVC Controller 接口已自动生成文档，包含请求/响应示例和在线调试功能。如需 JWT 认证，点击页面右上角 "Authorize" 按钮填入 Token 即可。

## 配置属性

```yaml
letool:
  swagger:
    enabled: true                 # 是否启用，默认 true
    title: "API Documentation"    # 文档标题
    description: ""               # 文档描述
    version: "1.0.0"             # 版本号
    contact:
      name: ""                   # 联系人姓名
      email: ""                  # 联系人邮箱
      url: ""                    # 联系人主页
    security:
      bearer-token: true         # 启用 Bearer Token 认证，默认 true
      header-name: "Authorization"  # Token 请求头名称
    knife4j:
      offline-docs: false        # 启用离线文档（MD/HTML），默认 false
      enable-footer: false       # 启用自定义页脚，默认 false
    groups:
      - name: "用户模块"          # API 分组名
        base-package: "com.example.controller"  # 扫描包路径
```

## 核心 API 示例

### 1. 注解声明式：零代码自动配置

引入本 starter 后无需编写任何 Java 配置代码。`SwaggerAutoConfiguration` 自动创建 `OpenAPI` Bean 和 `GroupedOpenApi` Bean，激活条件为 classpath 存在 `OpenAPI` 类且 `letool.swagger.enabled=true`（默认为 true）。

自动配置行为：
- **OpenAPI Bean** — 根据 `SwaggerProperties` 构建标题、描述、版本、联系人等信息；当 `security.bearer-token=true` 时自动注册 HTTP Bearer JWT 安全方案并设为全局要求。
- **GroupedOpenApi Bean** — 扫描 `com.github.leyland` 包（letool 框架端点）；若配置了 `groups` 列表则合并其 `basePackage` 到扫描范围。

### 2. 编程式：自定义 OpenAPI Bean

如需更细粒度控制，可自行注册 Bean 覆盖默认配置：

```java
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("自定义文档")
                .version("2.0")
                .description("覆盖默认配置的文档")
                .contact(new Contact().name("开发者").email("dev@test.com")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"))
            .schemaRequirement("Bearer",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
    }
}
```

### 3. 编程式：多分组配置

默认将所有配置的包路径合并为一个默认分组。如需实现真正意义上的多分组切换（右上角下拉菜单），手动注册额外的 `GroupedOpenApi` Bean：

```java
@Bean
public GroupedOpenApi userGroupApi() {
    return GroupedOpenApi.builder()
        .group("用户模块")
        .packagesToScan("com.example.user.controller")
        .build();
}

@Bean
public GroupedOpenApi orderGroupApi() {
    return GroupedOpenApi.builder()
        .group("订单模块")
        .packagesToScan("com.example.order.controller")
        .build();
}
```

### 4. 安全认证配置

Bearer Token 启用后，Swagger UI 自动添加 "Authorize" 按钮，用户填入 JWT Token 后所有接口请求将携带 `Authorization: Bearer <token>` 请求头：

```yaml
letool:
  swagger:
    security:
      bearer-token: true
      header-name: "Authorization"
```

底层通过 `openApi.schemaRequirement("Bearer", ...)` 和 `openApi.addSecurityItem(new SecurityRequirement().addList("Bearer"))` 注入 JWT Bearer 安全方案。

### 5. 关闭 Swagger

```yaml
letool:
  swagger:
    enabled: false   # 禁用（生产环境推荐）
```

配置为 `false` 后，`SwaggerAutoConfiguration` 因 `@ConditionalOnProperty` 条件不满足而跳过，文档页面和 API 端点均不可访问。
