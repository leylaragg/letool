package io.github.leylaragg.letool.security.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoginUser} 集合状态的防御性测试。
 */
class LoginUserSafetyTest {

    /**
     * 验证构造后修改调用方集合不会改变已认证用户的角色快照。
     */
    @Test
    void constructorShouldDefensivelyCopyAuthorities() {
        List<String> roles = new ArrayList<>(List.of("ADMIN"));
        LoginUser user = new LoginUser(1L, "admin", roles, List.of("user:read"));

        roles.add("AUDITOR");

        assertEquals(List.of("ADMIN"), user.getRoles());
        assertThrows(UnsupportedOperationException.class, () -> user.getRoles().add("ROOT"));
    }

    /**
     * 验证空集合输入会被规范为空快照，避免安全上下文读取时发生空指针异常。
     */
    @Test
    void settersShouldNormalizeNullAuthorities() {
        LoginUser user = new LoginUser();

        user.setRoles(null);
        user.setPermissions(null);

        assertTrue(user.getRoles().isEmpty());
        assertTrue(user.getPermissions().isEmpty());
    }
}
