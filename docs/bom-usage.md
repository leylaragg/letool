# BOM and Dependency Management Usage

letool currently publishes the root POM artifact as the dependency management entry point.

## Import letool Dependency Management

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.leylaragg</groupId>
            <artifactId>letool</artifactId>
            <version>3.1.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The example imports the BOM matching the current source version; install the root POM first. For Central consumers, use a BOM version that has actually been published. This BOM selects base modules `3.1.1` and printing modules `3.1.3`; its own version does not force all JARs to share a version.

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

The current Letool baseline is aligned with Spring Boot `3.5.x`. In application projects, prefer importing the Spring Boot BOM first through the normal Spring Boot parent or dependency management, then import letool for letool module versions. External libraries already managed by Boot, including Netty, reuse the Boot BOM version instead of declaring a second version property.

## Starter 选择边界

- OSS、SMS、Pay、MQ 等外部能力应只引入实际使用的一个 Provider 模块；Provider 模块会传递核心契约。
- AI 模块不传递具体模型厂商，应用仍需自行选择 Spring AI Provider Starter。
- Mock 必须显式启用，只用于开发和自动化测试，不能作为外部服务生产能力。
- 外部 SDK/Binder 的账号、网络、费用、确认和可靠性语义仍需在目标环境验证。

## Independent Module Upgrades

Use the root `io.github.leylaragg:letool:pom` as the version catalog. Publish a new catalog version when its selected versions change; this does not require republishing unchanged JARs. Applications that manage starters explicitly, such as EDC, can use separate `letool.version` and `letool.print.version` properties instead of importing the catalog.
