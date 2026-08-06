package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.cipher.sm.Sm2Util;
import com.github.leyland.letool.cipher.util.CipherUtil;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 letool-starter-cipher-suite 安全加密能力。
 */
@RestController
@RequestMapping("/api/public/cipher")
public class CipherController {

    /**
     * 演示带附加认证数据的 AES-GCM 加解密。
     *
     * @param text 待加密文本
     * @return 明文、版本化密文和解密结果
     */
    @GetMapping("/aes")
    public R<Map<String, String>> aes(@RequestParam(defaultValue = "Hello AES!") String text) {
        String key = CipherUtil.generateAesKey(256);
        String authenticatedData = "sample-tenant";
        String encrypted = CipherUtil.aesEncrypt(text, key, authenticatedData);
        String decrypted = CipherUtil.aesDecrypt(encrypted, key, authenticatedData);
        return R.ok(Map.of("original", text, "encrypted", encrypted, "decrypted", decrypted));
    }

    /**
     * 演示 SM2 标准曲线短数据加解密。
     *
     * @param text 待加密短文本
     * @return 明文、密文和解密结果
     */
    @GetMapping("/sm2")
    public R<Map<String, String>> sm2(@RequestParam(defaultValue = "Hello 国密SM2!") String text) {
        Sm2Util.Sm2KeyPair keyPair = CipherUtil.generateSm2KeyPair();
        String encrypted = CipherUtil.sm2Encrypt(text, keyPair.getPublicKey());
        String decrypted = CipherUtil.sm2Decrypt(encrypted, keyPair.getPrivateKey());
        return R.ok(Map.of("original", text, "encrypted", encrypted, "decrypted", decrypted));
    }

    /**
     * 演示 SHA-2 与 SM3 摘要。
     *
     * @param text 待摘要文本
     * @return 各摘要算法结果
     */
    @GetMapping("/hash")
    public R<Map<String, String>> hash(@RequestParam(defaultValue = "letool") String text) {
        return R.ok(Map.of(
                "sha256", CipherUtil.sha256(text),
                "sha512", CipherUtil.sha512(text),
                "sm3", CipherUtil.sm3(text)
        ));
    }
}
