# letool-starter-swagger

## 模块定位

`letool-starter-swagger` 为 Spring Boot 3.5.x 项目提供开箱即用的 API 文档能力：

- Springdoc 2.8.17 负责 OpenAPI 文档生成、Controller 扫描、分组与扩展；
- Knife4j 4.5.0 纯 UI 提供 `/doc.html` 增强界面；
- Letool 提供常用文档信息、Bearer JWT 默认方案、用户配置退让和统一关闭开关。

模块不会重写 OpenAPI 引擎，也不会伪造分组。使用者既可以零配置直接使用，也可以通过
Springdoc 原生配置或自定义 `OpenAPI` Bean 完全接管。

## Maven 依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-swagger</artifactId>
    <version>${letool.version}</version>
</dependency>
```

Starter 已传递提供 Spring MVC、Springdoc WebMVC UI 和 Knife4j 纯 UI，不需要业务项目重复声明。

## 零配置使用

启动 Servlet Web 应用后即可访问：

| 地址 | 说明 |
|---|---|
| `/doc.html` | Knife4j 增强文档界面，推荐作为日常入口 |
| `/v3/api-docs` | OpenAPI 3 JSON 文档 |
| `/v3/api-docs.yaml` | OpenAPI 3 YAML 文档 |
| `/swagger-ui.html` | Springdoc Swagger UI 兼容入口 |
| `/swagger-ui/index.html` | Springdoc Swagger UI 页面 |

默认文档标题为 `API Documentation`，版本为 `1.0.0`，并声明名称为 `Bearer` 的标准
HTTP Bearer JWT 安全方案。

## Letool 配置

模块只保留 9 项真实生效的便利配置：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `letool.swagger.enabled` | `true` | 是否启用并开放 API 文档入口 |
| `letool.swagger.title` | `API Documentation` | 文档标题 |
| `letool.swagger.description` | 空字符串 | 文档描述 |
| `letool.swagger.version` | `1.0.0` | 文档版本 |
| `letool.swagger.contact.name` | `null` | 联系人姓名 |
| `letool.swagger.contact.email` | `null` | 联系人邮箱 |
| `letool.swagger.contact.url` | `null` | 联系人主页 |
| `letool.swagger.security.bearer-token` | `true` | 是否声明全局 HTTP Bearer JWT 安全方案 |
| `letool.swagger.security.scheme-name` | `Bearer` | OpenAPI 安全方案名称 |

完整示例：

```yaml
letool:
  swagger:
    enabled: true
    title: "订单服务 API"
    description: "订单查询与履约接口"
    version: "2.1.0"
    contact:
      name: "订单平台团队"
      email: "order@example.com"
      url: "https://example.com/order"
    security:
      bearer-token: true
      scheme-name: "Bearer"
```

联系人字段全部为空时，不会向最终 OpenAPI 文档写入空联系人对象。开启 Bearer 时，
`scheme-name` 不能为空白；否则应用启动失败，避免生成不可用的安全契约。关闭 Bearer 后，
该名称不再参与校验。

## 关闭 API 文档

生产环境可以使用一个 Letool 开关统一关闭文档入口：

```yaml
letool:
  swagger:
    enabled: false
```

关闭后，Knife4j、OpenAPI JSON/YAML、Springdoc Swagger UI 及其配置子路径统一返回 404，
普通业务接口继续正常工作。过滤器能够识别 `server.servlet.context-path` 和
`spring.mvc.servlet.path`，也会读取 `springdoc.api-docs.path`、`springdoc.swagger-ui.path`
及真实 `GroupedOpenApi` 分组后精确拦截自定义入口，不会封锁自定义路径下的整个业务子树。

该开关只负责是否开放文档，不替代身份认证和授权。需要在生产环境按用户、角色或网络范围开放
文档时，应保持文档启用，并通过 Spring Security、API 网关或反向代理配置访问控制。

仍可使用 Springdoc 原生开关单独控制底层能力：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

`springdoc.api-docs.enabled=false` 时，Letool 不创建默认 `OpenAPI` Bean。

## 扫描范围与真实分组

使用 Springdoc 原生配置限制扫描范围：

```yaml
springdoc:
  packages-to-scan:
    - com.example.user.controller
    - com.example.order.controller
  paths-to-match:
    - /users/**
    - /orders/**
```

使用 `springdoc.group-configs` 创建相互独立的真实文档分组：

```yaml
springdoc:
  group-configs:
    - group: user-api
      packages-to-scan:
        - com.example.user.controller
      paths-to-match:
        - /users/**
    - group: order-api
      packages-to-scan:
        - com.example.order.controller
      paths-to-match:
        - /orders/**
```

Letool 不创建 `GroupedOpenApi` 默认 Bean，也不提供 `letool.swagger.groups` 伪分组配置。

## Bearer JWT 安全方案

默认安全方案名称为 `Bearer`。接口注解应引用相同名称：

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "Bearer")
public class UserController {

    @Operation(summary = "查询当前用户")
    @GetMapping("/users/me")
    public String currentUser() {
        return "current-user";
    }
}
```

如果项目使用其他方案名，可同时修改配置和注解：

```yaml
letool:
  swagger:
    security:
      scheme-name: InternalToken
```

如果只有部分接口需要认证，或项目要自行维护多个安全方案，可以设置
`letool.swagger.security.bearer-token=false`，再通过自定义 `OpenAPI` Bean 或 Springdoc
`OpenApiCustomizer` 注册实际方案。文档声明不会创建 Spring Security 认证链，真实鉴权仍由业务应用负责。

## 扩展与完全接管

业务应用提供自己的 `OpenAPI` Bean 后，Letool 默认 Bean 按类型自动退让：

```java
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI projectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("完全自定义文档")
                        .version("3.0.0"));
    }
}
```

希望保留 Letool 默认信息并增量扩展时，直接注册 Springdoc 原生的 `OpenApiCustomizer`、
`OperationCustomizer` 或 `GroupedOpenApi` Bean。Springdoc 的扩展点不会被 Letool 屏蔽。

## Knife4j 能力边界

本模块只引入 `knife4j-openapi3-ui`，用于提供常用的增强文档界面；不会引入绑定较旧 Springdoc
版本的完整 Knife4j Starter，也不会替用户开启网关聚合、服务端增强、离线文档、页脚或自定义请求头等
能力。需要这些能力的项目应根据实际架构显式选择 Knife4j 配置，并自行验证版本兼容性。

## 迁移说明

此前 Springdoc-only 调整中的错误方向已纠正：

- `/doc.html` 已恢复，由 Knife4j 纯 UI 提供；
- `letool.swagger.enabled` 已恢复为真实统一开关；
- Bearer 默认恢复为开启，默认方案名恢复为 `Bearer`，并允许通过配置修改；
- Springdoc 仍负责 OpenAPI 引擎、扫描、分组和原生扩展。

以下无效或重复能力不恢复：`ApiGroup`、`letool.swagger.groups`、自动 `defaultGroupApi`、
`security.header-name`、`knife4j.offline-docs` 和 `knife4j.enable-footer`。项目应分别使用 OpenAPI 注解、
`springdoc.group-configs`、标准 `Authorization` Bearer 头或业务自定义扩展完成对应需求。
