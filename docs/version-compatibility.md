# Version Compatibility Matrix

## Current Baseline

| Item | Supported Version | Notes |
|---|---|---|
| letool | `2.0.0-beta.1` | Beta line; API and dependency scopes may still change. |
| Java | `17+` | Java 17 is the build baseline; Java 21 is recommended for virtual-thread scenarios. |
| Maven | `3.9+` | CI should use Maven Wrapper when possible. |
| Spring Boot | `3.5.x` | Parent POM currently manages `3.5.16`. |
| Spring Framework | `6.2.x` | Managed by Spring Boot `3.5.x`. |
| Spring Cloud | `2025.0.x` | Parent POM currently imports `2025.0.3`. |
| Netty | Boot-managed | `letool-starter-net` follows the Netty version supplied by the Spring Boot BOM. |
| Jakarta EE APIs | Jakarta namespace | Spring Boot 3 baseline; `javax.*` integrations are not targeted. |

## Compatibility Policy

| letool Line | Java Baseline | Spring Boot Baseline | Status |
|---|---|---|---|
| `2.0.x` | 17 | 3.5.x | Planned stable line. |
| `2.0.0-beta.x` | 17 | 3.5.x | Active hardening; production readiness varies by module. |

## Module Notes

| Module Area | Compatibility Risk |
|---|---|
| Servlet/web/security/swagger | Must stay aligned with Spring Boot 3 and Spring Framework 6 Servlet stack. |
| Cache/distributed-lock | Redis and Redisson versions must be verified with Spring Boot dependency management before stable release. |
| Mail | Uses Jakarta Mail runtime; no `javax.mail` compatibility guarantee. |
| Excel/rule | EasyExcel 4.0.3、Commons Compress 1.28.0 与 Commons IO 2.20.0 在父 POM 统一收敛；规则引擎版本同样由父 POM 管理。 |
| Net | Netty is managed by the Boot BOM; TCP behavior additionally depends on the remote protocol framing and request-correlation contract. |
| OSS/SMS/Pay | 已隔离官方 SDK Provider；仍需在实际账号或沙箱验证厂商契约。 |
| MQ | 基于 Spring Cloud Stream 2025.0.x；Binder/Broker 兼容性及可靠性配置由目标应用验证。 |

## Release Gate

Before promoting a beta to stable:

- Run full test suite with Java 17.
- Run CI smoke test with Java 21.
- Review all starter dependency scopes.
- Confirm sample application starts with common starter combinations.
- Update this matrix for any Spring Boot or Java baseline change.
