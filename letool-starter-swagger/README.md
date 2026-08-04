# letool-starter-swagger

## 模块定位

`letool-starter-swagger` 是面向 Spring Boot 3.5.x 与 Springdoc 2.8.17 的 WebMVC API 文档薄封装。
模块直接提供 Springdoc 原生 OpenAPI JSON 与 Swagger UI，只替业务应用组装常用的文档信息和可选
Bearer JWT 安全方案，不重复实现端点、界面、控制器扫描或文档分组。

## Maven 依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-swagger</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块直接依赖 `springdoc-openapi-starter-webmvc-ui` 2.8.17 和
`spring-boot-starter-web`。应用引入本 Starter 后，无需再次声明这两个依赖。

## 快速开始

在 `application.yml` 中配置项目文档信息：

```yaml
letool:
  swagger:
    title: "项目接口文档"
    description: "RESTful API 接口文档"
    version: "1.0.0"
    contact:
      name: "开发团队"
      email: "dev@example.com"
      url: "https://example.com"
    security:
      bearer-token: false
```

启动 Servlet Web 应用后，可以访问：

| 地址 | 说明 |
|---|---|
| `/v3/api-docs` | OpenAPI 3 JSON 文档 |
| `/swagger-ui.html` | Swagger UI 兼容入口，会重定向到实际页面 |
| `/swagger-ui/index.html` | Swagger UI 实际页面 |

## 配置边界

Letool 仅保留 7 项业务便利配置：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `letool.swagger.title` | `API Documentation` | 文档标题 |
| `letool.swagger.description` | 空字符串 | 文档描述 |
| `letool.swagger.version` | `1.0.0` | 文档版本 |
| `letool.swagger.contact.name` | `null` | 联系人姓名 |
| `letool.swagger.contact.email` | `null` | 联系人邮箱 |
| `letool.swagger.contact.url` | `null` | 联系人主页 |
| `letool.swagger.security.bearer-token` | `false` | 是否声明全局标准 HTTP Bearer JWT 安全方案 |

联系人三个字段全部为空时，不会向最终 OpenAPI 文档写入空联系人对象。

文档端点、Swagger UI、扫描范围和分组由 Springdoc 原生 `springdoc.*` 配置管理。
这种边界让业务项目保留开箱即用的文档信息配置，同时直接获得 Springdoc 的完整能力和升级路径。

## 生产环境关闭文档

生产环境要同时关闭 OpenAPI JSON 端点和 Swagger UI：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

只关闭其中一个开关不会关闭另一项能力。`springdoc.api-docs.enabled=false` 时，
Letool 也不会创建默认 `OpenAPI` Bean。

## 控制器扫描

使用 Springdoc 原生配置限制扫描包：

```yaml
springdoc:
  packages-to-scan:
    - com.example.user.controller
    - com.example.order.controller
```

未配置扫描范围时，Springdoc 按应用上下文和控制器映射生成文档，Letool 不额外限定包路径。

## 多分组

使用 `springdoc.group-configs` 创建真实、相互独立的文档分组：

```yaml
springdoc:
  group-configs:
    - group: user
      display-name: 用户接口
      packages-to-scan:
        - com.example.user.controller
    - group: order
      display-name: 订单接口
      packages-to-scan:
        - com.example.order.controller
      paths-to-match:
        - /orders/**
```

Springdoc 会为每个分组生成独立文档，并在 Swagger UI 中提供分组选择。Letool 不创建默认
`GroupedOpenApi` Bean，也不会把多个扫描包合并为伪分组。

## 接口注解

接口说明直接使用 Swagger OpenAPI 3 注解：

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理", description = "用户查询与维护接口")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/users")
public class UserController {

    @Operation(summary = "查询当前用户")
    @GetMapping("/current")
    public String currentUser() {
        return "current-user";
    }
}
```

`@Tag` 用于组织接口标签，`@Operation` 用于描述单个操作。全局 Bearer 未开启时，可在需要认证的
Controller 或接口上使用 `@SecurityRequirement(name = "BearerAuth")`；此时应用还需自行提供同名
安全方案，或通过自定义 `OpenAPI` Bean / `OpenApiCustomizer` 注册该方案。

## Bearer JWT 安全方案

Bearer 默认关闭：

```yaml
letool:
  swagger:
    security:
      bearer-token: false
```

默认情况下 Letool 不声明安全方案，也不为所有接口附加安全要求。显式开启后：

```yaml
letool:
  swagger:
    security:
      bearer-token: true
```

Letool 会注册名为 `BearerAuth` 的标准 HTTP Bearer JWT 安全方案，并把它声明为 OpenAPI 全局
安全要求。Swagger UI 会展示授权入口，并使用标准 `Authorization: Bearer <token>` 请求头。
这只是文档契约，不会创建 Spring Security 认证链；实际鉴权仍由应用的安全配置负责。

如果只有部分接口需要认证，建议保持全局 Bearer 关闭，并在对应 Controller 或接口上使用
`@SecurityRequirement(name = "BearerAuth")`，同时由应用注册 `BearerAuth` 安全方案。

## 扩展与接管

### 完全接管 OpenAPI Bean

当应用提供自己的 `OpenAPI` Bean 时，Letool 自动退让，只保留用户 Bean：

```java
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI businessOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("业务接口文档")
                        .description("由业务应用完全接管")
                        .version("2.0.0"));
    }
}
```

### 增量定制 OpenAPI

希望保留 Letool 文档信息，只追加服务器、标签或其他模型时，可以注册 Springdoc 原生
`OpenApiCustomizer`：

```java
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiCustomizationConfiguration {

    @Bean
    public OpenApiCustomizer productionServerCustomizer() {
        return openApi -> openApi.addServersItem(
                new Server()
                        .url("https://api.example.com")
                        .description("生产环境"));
    }
}
```

## Knife4j 边界

本模块不内置 Knife4j。业务应用如果确实需要 Knife4j 增强界面，可以自行显式选择对应依赖和配置，
但必须自行验证其与当前 Spring Boot、Springdoc 版本的兼容性。Letool 不覆盖 Springdoc 版本来适配
第三方界面，也不承诺 `/doc.html` 可用。

## BREAKING 迁移指南

本次重构删除了未生效或重复封装 Springdoc 的旧配置和 API：

| 旧用法 | 新用法 | 迁移说明 |
|---|---|---|
| `/doc.html` | `/swagger-ui.html` 或 `/swagger-ui/index.html` | 改用 Springdoc 原生 Swagger UI |
| `letool.swagger.enabled` | `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled` | 需要关闭文档时必须同时关闭两个原生开关 |
| `letool.swagger.groups` | `springdoc.group-configs` | 使用 Springdoc 创建真实多分组 |
| `@ApiGroup` | `@Tag`、`@Operation` 或 `springdoc.group-configs` | 删除无人处理的伪分组注解 |
| Bearer 安全方案默认启用 | `letool.swagger.security.bearer-token=true` | 新版本默认关闭 Bearer；需要全局安全声明的应用必须显式开启 |
| `@SecurityRequirement(name = "Bearer")` | `@SecurityRequirement(name = "BearerAuth")` | 内置安全方案名统一为 `BearerAuth`，已有注解必须同步改名 |
| `letool.swagger.security.header-name` | 无 | 标准 HTTP Bearer 固定使用 `Authorization` 请求头 |
| `letool.swagger.knife4j.offline-docs` | 无 | 删除未实现的离线文档配置 |
| `letool.swagger.knife4j.enable-footer` | 无 | 删除未实现的页脚配置 |
| 自动创建 `defaultGroupApi` | `springdoc.packages-to-scan` 或 `springdoc.group-configs` | Letool 不再创建或合并默认分组 |
| Starter 内置 Knife4j | 应用自行显式引入所需 Knife4j 依赖 | Letool 不再内置 Knife4j；仍需增强界面的应用负责验证其与当前 Spring Boot、Springdoc 版本的兼容性 |

升级后应删除所有旧配置，按本文使用 Springdoc 原生能力完成端点、界面、扫描和分组设置。
