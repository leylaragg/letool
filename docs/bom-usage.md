# BOM and Dependency Management Usage

letool currently publishes the root POM artifact as the dependency management entry point.

## Import letool Dependency Management

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.leylaragg</groupId>
            <artifactId>letool</artifactId>
            <version>2.0.0-beta.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

After importing the POM, application projects can omit individual letool starter versions:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.leylaragg</groupId>
        <artifactId>letool-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.leylaragg</groupId>
        <artifactId>letool-starter-cache</artifactId>
    </dependency>
</dependencies>
```

## Spring Boot Alignment

letool `2.0.0-beta.1` is aligned with Spring Boot `3.5.x`. In application projects, prefer importing the Spring Boot BOM first through the normal Spring Boot parent or dependency management, then import letool for letool module versions. External libraries already managed by Boot, including Netty, reuse the Boot BOM version instead of declaring a second version property.

## Starter 选择边界

- OSS、SMS、Pay、MQ 等外部能力应只引入实际使用的一个 Provider 模块；Provider 模块会传递核心契约。
- AI 模块不传递具体模型厂商，应用仍需自行选择 Spring AI Provider Starter。
- Mock 必须显式启用，只用于开发和自动化测试，不能作为外部服务生产能力。
- 外部 SDK/Binder 的账号、网络、费用、确认和可靠性语义仍需在目标环境验证。

## Future Dedicated BOM

A dedicated `letool-bom` artifact is recommended before the stable 2.0.0 release. Until then, use the root `io.github.leylaragg:letool:pom` import shown above.
