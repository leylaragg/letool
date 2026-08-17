package io.github.leylaragg.letool.cipher.support;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;

/**
 * Bouncy Castle Provider 内部访问入口。
 *
 * <p>模块直接向 JCA 传入私有 Provider 实例，不修改 JVM 全局 Provider 列表及优先级。</p>
 */
public final class BouncyCastleSupport {

    private static final Provider PROVIDER = new BouncyCastleProvider();

    /** 工具类禁止实例化。 */
    private BouncyCastleSupport() {
    }

    /**
     * 获取模块私有的 Bouncy Castle Provider。
     *
     * @return 可安全复用的 Provider 实例
     */
    public static Provider provider() {
        return PROVIDER;
    }
}
