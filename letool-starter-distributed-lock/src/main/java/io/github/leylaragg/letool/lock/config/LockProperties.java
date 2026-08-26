package io.github.leylaragg.letool.lock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后端无关的分布式锁功能开关。
 *
 * <p>锁 key 前缀、公平性等实现参数由具体后端模块管理，避免通用契约反向依赖 Redis。</p>
 */
@ConfigurationProperties(prefix = "letool.lock")
public class LockProperties {

    private boolean enabled = true;
    private final Idempotent idempotent = new Idempotent();

    /** @return 是否启用通用锁模板和切面 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用通用锁模板和切面 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 幂等切面开关 */
    public Idempotent getIdempotent() {
        return idempotent;
    }

    /** 幂等能力的通用开关。 */
    public static class Idempotent {

        private boolean enabled = true;

        /** @return 是否启用幂等服务和切面 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否启用幂等服务和切面 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
