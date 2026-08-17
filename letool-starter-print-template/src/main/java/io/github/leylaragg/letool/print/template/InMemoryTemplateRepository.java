package io.github.leylaragg.letool.print.template;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于不可变状态快照的内存模板仓库。
 *
 * @author leyland
 */
public final class InMemoryTemplateRepository implements TemplateRepository {

    /** 发布版本和当前指针始终通过同一个引用读写。 */
    private final AtomicReference<State> state = new AtomicReference<>(State.empty());

    @Override
    public Optional<TemplateSet> find(long version) {
        requirePositiveVersion(version);
        return Optional.ofNullable(state.get().versions.get(version));
    }

    @Override
    public Optional<TemplateSet> current() {
        State snapshot = state.get();
        if (snapshot.currentVersion == null) {
            return Optional.empty();
        }
        return Optional.of(snapshot.versions.get(snapshot.currentVersion));
    }

    @Override
    public TemplateSet publish(TemplateSet templateSet) {
        return publish(templateSet, false);
    }

    @Override
    public TemplateSet publishAndActivate(TemplateSet templateSet) {
        return publish(templateSet, true);
    }

    @Override
    public TemplateSet activate(long version) {
        requirePositiveVersion(version);
        while (true) {
            State current = state.get();
            TemplateSet target = current.versions.get(version);
            if (target == null) {
                throw invalid("模板集合版本尚未发布：" + version);
            }
            if (Objects.equals(current.currentVersion, version)) {
                return target;
            }
            State updated = new State(current.versions, version);
            if (state.compareAndSet(current, updated)) {
                return target;
            }
        }
    }

    /** 发布路径通过同一个 CAS 控制版本写入和可选激活。 */
    private TemplateSet publish(TemplateSet templateSet, boolean activate) {
        Objects.requireNonNull(templateSet, "templateSet 不能为空");
        long version = templateSet.version();
        requirePositiveVersion(version);
        while (true) {
            State current = state.get();
            if (current.versions.containsKey(version)) {
                throw invalid("模板集合版本已发布：" + version);
            }

            Map<Long, TemplateSet> versions = new HashMap<>(current.versions);
            versions.put(version, templateSet);
            Long currentVersion = activate ? Long.valueOf(version) : current.currentVersion;
            State updated = new State(versions, currentVersion);
            if (state.compareAndSet(current, updated)) {
                return templateSet;
            }
        }
    }

    /** 校验对外使用的版本号。 */
    private void requirePositiveVersion(long version) {
        if (version <= 0) {
            throw invalid("模板集合版本必须为正整数");
        }
    }

    /** 创建不包含模板正文的业务异常。 */
    private PrintValidationException invalid(String detail) {
        return PrintValidationException.invalidRequest(detail);
    }

    /**
     * 仓库的单次一致性快照。
     *
     * @author leyland
     */
    private static final class State {

        /** 所有已发布版本。 */
        private final Map<Long, TemplateSet> versions;

        /** 当前激活版本，未激活时为空。 */
        private final Long currentVersion;

        /** 冻结一次仓库状态。 */
        private State(Map<Long, TemplateSet> versions, Long currentVersion) {
            this.versions = Map.copyOf(versions);
            this.currentVersion = currentVersion;
        }

        /** 创建空仓库状态。 */
        private static State empty() {
            return new State(Map.of(), null);
        }
    }
}
