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
 * 演示 letool-starter-cipher-suite 加解密.
 */
@RestController
@RequestMapping("/api/public/cipher")
public class CipherController {

    @GetMapping("/aes")
    public R<Map<String, String>> aes(@RequestParam(defaultValue = "Hello AES!") String text) {
        String key = CipherUtil.generateAesKey(256);
        String encrypted = CipherUtil.aesEncrypt(text, key);
        String decrypted = CipherUtil.aesDecrypt(encrypted, key);
        return R.ok(Map.of("original", text, "encrypted", encrypted, "decrypted", decrypted));
    }

    @GetMapping("/sm2")
    public R<Map<String, String>> sm2(@RequestParam(defaultValue = "Hello 国密SM2!") String text) {
        Sm2Util.Sm2KeyPair keyPair = CipherUtil.generateSm2KeyPair();
        String encrypted = CipherUtil.sm2Encrypt(text, keyPair.getPublicKey());
        String decrypted = CipherUtil.sm2Decrypt(encrypted, keyPair.getPrivateKey());
        return R.ok(Map.of("original", text, "encrypted", encrypted, "decrypted", decrypted));
    }

    @GetMapping("/hash")
    public R<Map<String, String>> hash(@RequestParam(defaultValue = "letool") String text) {
        return R.ok(Map.of(
                "md5", CipherUtil.md5(text),
                "sha256", CipherUtil.sha256(text),
                "sm3", CipherUtil.sm3(text)
        ));
    }
}
