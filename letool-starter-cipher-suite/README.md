# letool-starter-cipher-suite

## 模块简介

`letool-starter-cipher-suite` 是企业级加密套件模块，统一封装 **AES / RSA 非对称 / SM2 / SM3 / SM4 国密算法**，以及 **MD5 / SHA-256 / SHA-512 哈希**、**HMAC 消息认证**、**数字签名与验签**。通过 `CipherUtil` 提供统一静态 API，所有方法均使用 Base64 编码输入输出，简化加解密操作。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-cipher-suite</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-cipher-suite</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. AES 对称加解密

```java
String key = CipherUtil.generateAesKey(256);
String enc = CipherUtil.aesEncrypt("Hello World", key);
String dec = CipherUtil.aesDecrypt(enc, key);
```

### 3. RSA 非对称加解密

```java
RsaCipher.RsaKeyPair pair = CipherUtil.generateRsaKeyPair(2048);
String enc = CipherUtil.rsaEncrypt("Secret", pair.getPublicKey());
String dec = CipherUtil.rsaDecrypt(enc, pair.getPrivateKey());
```

### 4. 哈希与签名

```java
String md5 = CipherUtil.md5("hello");
String sha256 = CipherUtil.sha256("hello");
String sig = CipherUtil.sign("data", privateKey);
boolean valid = CipherUtil.verify("data", sig, publicKey);
```

## 核心 API 示例

`CipherUtil` 是所有加解密操作的统一入口，所有方法均为静态方法。

### 1. AES 对称加密

```java
// 生成 128/192/256 位 AES 密钥（返回 Base64 编码）
String key = CipherUtil.generateAesKey(256);

// 加密（默认 GCM 模式）
String encrypted = CipherUtil.aesEncrypt("Hello World", key);

// 解密
String decrypted = CipherUtil.aesDecrypt(encrypted, key);

// 指定加密模式
String encrypted = CipherUtil.aesEncrypt("Hello World", key, CipherMode.CBC);
```

### 2. RSA 非对称加密

```java
// 生成 RSA 密钥对（1024/2048/4096 位）
RsaCipher.RsaKeyPair pair = CipherUtil.generateRsaKeyPair(2048);
String publicKey = pair.getPublicKey();    // Base64 编码的公钥
String privateKey = pair.getPrivateKey();  // Base64 编码的私钥

// 公钥加密
String encrypted = CipherUtil.rsaEncrypt("Secret Message", publicKey);

// 私钥解密
String decrypted = CipherUtil.rsaDecrypt(encrypted, privateKey);
```

### 3. 哈希算法

```java
// MD5
String md5 = CipherUtil.md5("hello");

// SHA-256
String sha256 = CipherUtil.sha256("hello");

// SHA-512
String sha512 = CipherUtil.sha512("hello");
```

### 4. HMAC 消息认证

```java
String key = CipherUtil.generateHmacKey();

// HMAC-SHA256（十六进制输出）
String hmac = CipherUtil.hmacSha256("data", key);

// HMAC-SHA256（Base64 输出）
String hmacBase64 = CipherUtil.hmacSha256Base64("data", key);

// HMAC-SHA512（十六进制输出）
String hmac512 = CipherUtil.hmacSha512("data", key);
```

### 5. 数字签名与验签

```java
// 使用 RSA 私钥签名（默认算法）
String signature = CipherUtil.sign("data", privateKey);

// 签名（指定算法）
String signature = CipherUtil.sign("data", privateKey, "SHA256withRSA");

// 使用 RSA 公钥验签（默认算法）
boolean valid = CipherUtil.verify("data", signature, publicKey);

// 验签（指定算法）
boolean valid = CipherUtil.verify("data", signature, publicKey, "SHA256withRSA");
```

### 6. 国密算法

```java
// SM3 哈希（国密标准的哈希算法）
String sm3Hash = CipherUtil.sm3("hello");

// SM4 对称加密（国密标准的分组密码）
String sm4Key = CipherUtil.generateSm4Key();
String encrypted = CipherUtil.sm4Encrypt("Hello", sm4Key);
String decrypted = CipherUtil.sm4Decrypt(encrypted, sm4Key);

// SM2 非对称加密（国密标准的公钥密码）
Sm2Util.Sm2KeyPair sm2Pair = CipherUtil.generateSm2KeyPair();
String encrypted = CipherUtil.sm2Encrypt("Secret", sm2Pair.getPublicKey());
String decrypted = CipherUtil.sm2Decrypt(encrypted, sm2Pair.getPrivateKey());
```

### 7. 算法能力一览

| 类别 | 算法 | API 方法 |
|------|------|---------|
| 对称加密 | AES-128/192/256 (GCM/CBC) | `aesEncrypt()` / `aesDecrypt()` |
| 非对称加密 | RSA 1024/2048/4096 | `rsaEncrypt()` / `rsaDecrypt()` |
| 哈希 | MD5 | `md5()` |
| 哈希 | SHA-256 | `sha256()` |
| 哈希 | SHA-512 | `sha512()` |
| 消息认证 | HMAC-SHA256 | `hmacSha256()` / `hmacSha256Base64()` |
| 消息认证 | HMAC-SHA512 | `hmacSha512()` |
| 数字签名 | SHA256withRSA (默认) + 可指定算法 | `sign()` / `verify()` |
| 国密哈希 | SM3 | `sm3()` |
| 国密对称 | SM4 | `sm4Encrypt()` / `sm4Decrypt()` |
| 国密非对称 | SM2 | `sm2Encrypt()` / `sm2Decrypt()` |
| 密钥生成 | AES / RSA / SM4 / SM2 / HMAC | `generateAesKey()` / `generateRsaKeyPair()` / 等 |

## 配置属性

```yaml
letool:
  cipher:
    enabled: true                # 总开关
    aes-default-key-size: 256    # AES 默认密钥大小（128/192/256 位）
    rsa-default-key-size: 2048   # RSA 默认密钥大小（1024/2048/4096 位）
    sm-enabled: true             # 是否启用国密算法（SM2/SM3/SM4）
```
