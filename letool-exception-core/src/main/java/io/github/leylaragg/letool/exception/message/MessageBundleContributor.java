package io.github.leylaragg.letool.exception.message;

import java.util.Arrays;
import java.util.List;

/**
 * 描述需要参与异常国际化解析的附加消息资源包。
 *
 * <p>基础名称使用 Spring 资源包表示法，例如 {@code i18n/application/messages}。
 * 按照 Spring 约定，调用方不应包含语言后缀和文件扩展名；本类型只校验数组非空且每项不是空白字符串。</p>
 */
public final class MessageBundleContributor {

    private final List<String> basenames;

    private MessageBundleContributor(String[] basenames) {
        if (basenames == null || basenames.length == 0) {
            throw new IllegalArgumentException("basenames must not be null or empty");
        }

        String[] copy = basenames.clone();
        for (int index = 0; index < copy.length; index++) {
            if (copy[index] == null || copy[index].isBlank()) {
                throw new IllegalArgumentException(
                        "basenames[" + index + "] must not be blank");
            }
        }
        this.basenames = List.copyOf(Arrays.asList(copy));
    }

    /**
     * 为一个或多个 Spring 资源包基础名称创建贡献者。
     *
     * @param basenames 必填的 Spring 资源包基础名称
     * @return 包含基础名称防御性副本的不可变贡献者
     * @throws IllegalArgumentException 当数组为 {@code null}、空数组，
     *         或任一元素为 {@code null}、空白字符串时抛出
     */
    public static MessageBundleContributor of(String... basenames) {
        return new MessageBundleContributor(basenames);
    }

    /**
     * 按声明顺序获取配置的 Spring 资源包基础名称。
     *
     * @return 不可修改的基础名称列表
     */
    public List<String> getBasenames() {
        return basenames;
    }
}
