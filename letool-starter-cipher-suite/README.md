# letool-starter-cipher-suite

## 模块定位

`letool-starter-cipher-suite` 是面向业务开发的轻量密码工具包。模块使用 JDK JCA/JCE 实现标准算法，使用 Bouncy Castle 实现国密算法，通过 `CipherUtil` 提供开箱即用的安全默认 API。

模块负责：

- AES-GCM、SM4-GCM 认证加密；
- RSA-OAEP-SHA256 小数据加密与 RSA-PSS-SHA256 签名；
- SM2 公钥加密、SM3 摘要；
- HMAC-SHA256、HMAC-SHA512、SHA-256、SHA-512；
- 安全密钥生成、稳定错误码和严格输入校验。

模块不负责密钥托管、密钥轮换、证书生命周期、硬件密码设备、密码存储或合规认证。生产密钥应由 KMS、HSM 或受控密钥配置系统管理，不能写入代码和日志。

这是纯静态工具包，不需要 Spring Bean，也没有 `letool.cipher.*` 配置项。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-cipher-suite</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## AES-GCM 认证加密

```java
String key = CipherUtil.generateAesKey(256);

// tenantId 等业务上下文可作为 AAD 参与认证，但不会写入密文
String encrypted = CipherUtil.aesEncrypt("Hello World", key, "tenant-1001");
String decrypted = CipherUtil.aesDecrypt(encrypted, key, "tenant-1001");
```

AES 和 SM4 密文格式为：

```text
LT1.<算法>.<Base64URL随机数>.<Base64URL密文及认证标签>
```

版本、算法和 AAD 都参与认证。密文、随机数或 AAD 任一被修改，解密都会失败；模块不会自动降级尝试 CBC。

AES/SM4 字符串 API 面向内存中的业务数据，单次 UTF-8 明文上限为 16 MiB，密文封装会在 Base64URL 解码前执行对应长度校验。SM2 字符串 API 采用相同内存边界，但它只应用于短数据。更大的文件或数据流应使用流式对称加密方案，不能通过一次性字符串或公钥加密 API 处理。

## RSA 加密与签名

```java
RsaCipher.RsaKeyPair pair = CipherUtil.generateRsaKeyPair(2048);

// RSA 只用于会话密钥、令牌等短数据
String encrypted = CipherUtil.rsaEncrypt("session-key", pair.getPublicKey());
String decrypted = CipherUtil.rsaDecrypt(encrypted, pair.getPrivateKey());

// 固定使用 RSA-PSS-SHA256
String signature = CipherUtil.sign("payload", pair.getPrivateKey());
boolean valid = CipherUtil.verify("payload", signature, pair.getPublicKey());
```

密钥生成 API 只允许生成 2048、3072 或 4096 位 RSA 密钥；导入的 RSA 密钥最低为 2048 位。2048 位 RSA-OAEP-SHA256 的单块 UTF-8 明文上限为 190 字节；大数据请使用 AES-GCM 或 SM4-GCM 加密，再用 RSA 封装对称密钥。

## HMAC 消息认证

```java
// 返回 Base64 编码的 256 位随机密钥，可直接传给字符串 API
String key = CipherUtil.generateHmacKey();

String mac = CipherUtil.hmacSha256("payload", key);
boolean valid = CipherUtil.verifyHmacSha256("payload", mac, key);
```

HMAC 字符串密钥始终表示“Base64 编码的原始密钥”，不再把 Base64 文本本身当作密钥。SHA-256 校验值必须解码为 32 字节，校验 API 会在解码前限制编码长度并使用常量时间比较。

## 国密算法

```java
// SM3 摘要
String sm3 = CipherUtil.sm3("hello");

// SM4-GCM 认证加密
String sm4Key = CipherUtil.generateSm4Key();
String sm4Encrypted = CipherUtil.sm4Encrypt("payload", sm4Key, "order-1001");
String sm4Decrypted = CipherUtil.sm4Decrypt(sm4Encrypted, sm4Key, "order-1001");

// SM2 明确使用 sm2p256v1 曲线
Sm2Util.Sm2KeyPair sm2Pair = CipherUtil.generateSm2KeyPair();
String sm2Encrypted = CipherUtil.sm2Encrypt("short-data", sm2Pair.getPublicKey());
String sm2Decrypted = CipherUtil.sm2Decrypt(sm2Encrypted, sm2Pair.getPrivateKey());
```

模块使用私有 Bouncy Castle Provider 实例，不会修改 JVM 全局 Provider 顺序。

## 摘要与密码存储边界

```java
String sha256 = CipherUtil.sha256("payload");
String sha512 = CipherUtil.sha512("payload");
```

SHA-2 和 SM3 是快速摘要，不适合直接存储用户密码。密码存储应使用 Spring Security `PasswordEncoder` 提供的 Argon2、bcrypt、scrypt 或 PBKDF2。

`Md5Util` 和 `CipherUtil.md5()` 仅为必须兼容 MD5 的遗留非安全协议保留，已标记为弃用，不能用于密码、签名或安全完整性校验。

## 错误码

| 错误码 | 含义 |
|--------|------|
| `CIPHER_001` | 参数不满足算法约束 |
| `CIPHER_002` | 密钥编码、类型、曲线或长度无效 |
| `CIPHER_003` | 密文封装格式、版本或算法无效 |
| `CIPHER_004` | 加密执行失败 |
| `CIPHER_005` | 解密或认证失败 |
| `CIPHER_006` | 摘要、HMAC、签名或密钥生成失败 |

所有错误都通过继承 `SystemException` 的 `CipherException` 抛出。异常消息不会包含密钥、明文、密文或签名原值。

## 2.0 破坏性迁移说明

- 旧版没有 `LT1` 封装的 AES-GCM、AES-CBC 和 SM4-CBC 密文不能由新 API 自动解密，必须在升级前使用旧版本完成数据迁移。
- RSA 默认填充从 PKCS#1 v1.5 改为 OAEP-SHA256，旧 RSA 密文需要迁移。
- RSA 默认签名从 `SHA256withRSA` 改为 RSA-PSS-SHA256，旧签名不能由新默认验签 API 验证。
- HMAC 字符串密钥改为 Base64 原始密钥语义，旧系统如果传入普通文本密钥，需要先明确迁移方案。
- 删除 `CipherMode` 和 `CipherAlgorithm`；调用方应直接使用 `aesEncrypt`、`rsaEncrypt`、`sm2Encrypt`、`sm4Encrypt` 等明确算法 API。
- 删除 `RsaCipher.RSA_ALGORITHM_OAEP`；RSA 加解密已固定使用显式 OAEP-SHA256/MGF1-SHA256 参数，不再由调用方选择转换名称。
- 删除 `CipherException(String)` 和 `CipherException(String, Throwable)`；模块异常改由稳定错误码工厂创建，下游自定义业务异常不应直接实例化 `CipherException`。
- 删除任意字符串签名/摘要算法入口；分别改用固定的 RSA-PSS-SHA256、SHA-256 或 SHA-512 方法。
- 删除 `letool.cipher.*` 配置、空自动配置及其配置元数据；该模块现在是无需 Spring Bean 的纯静态工具包。

不要通过“解密失败后尝试旧算法”的方式兼容历史数据，这会重新引入降级攻击和不可控的数据判别逻辑。应在受控迁移程序中显式读取旧格式并重新加密。
