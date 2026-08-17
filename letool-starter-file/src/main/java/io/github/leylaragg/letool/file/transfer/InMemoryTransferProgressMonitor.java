package io.github.leylaragg.letool.file.transfer;

import io.github.leylaragg.letool.file.exception.FileErrorCode;
import io.github.leylaragg.letool.file.exception.FileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 有容量和终态保留边界的内存传输进度监视器。
 */
public final class InMemoryTransferProgressMonitor implements TransferProgressMonitor {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransferProgressMonitor.class);
    private static final Pattern TRANSFER_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Duration retention;
    private final int maxEntries;
    private final Duration notificationInterval;
    private final long notificationBytes;
    private final List<TransferProgressListener> listeners;
    private final Clock clock;
    private final ConcurrentHashMap<String, ProgressEntry> entries = new ConcurrentHashMap<>();

    /**
     * 使用系统 UTC 时钟创建监视器。
     *
     * @param retention 终态记录保留时长
     * @param maxEntries 最大记录数量
     * @param notificationInterval 最小通知时间间隔
     * @param notificationBytes 最小通知字节增量
     * @param listeners 有序进度监听器
     */
    public InMemoryTransferProgressMonitor(
            Duration retention,
            int maxEntries,
            Duration notificationInterval,
            long notificationBytes,
            List<TransferProgressListener> listeners) {
        this(retention, maxEntries, notificationInterval, notificationBytes,
                listeners, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建监视器，便于受控测试和时间策略扩展。
     *
     * @param retention 终态记录保留时长
     * @param maxEntries 最大记录数量
     * @param notificationInterval 最小通知时间间隔
     * @param notificationBytes 最小通知字节增量
     * @param listeners 有序进度监听器
     * @param clock 时间来源
     */
    public InMemoryTransferProgressMonitor(
            Duration retention,
            int maxEntries,
            Duration notificationInterval,
            long notificationBytes,
            List<TransferProgressListener> listeners,
            Clock clock) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention 必须大于 0");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 必须大于 0");
        }
        if (notificationInterval == null || notificationInterval.isNegative()) {
            throw new IllegalArgumentException("notificationInterval 不能小于 0");
        }
        if (notificationBytes <= 0) {
            throw new IllegalArgumentException("notificationBytes 必须大于 0");
        }
        this.retention = retention;
        this.maxEntries = maxEntries;
        this.notificationInterval = notificationInterval;
        this.notificationBytes = notificationBytes;
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String generateTransferId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public synchronized TransferProgress begin(
            String transferId,
            TransferType type,
            long totalBytes,
            long initialBytes) {
        String safeId = requireTransferId(transferId);
        Objects.requireNonNull(type, "type 不能为空");
        validateByteCounts(totalBytes, initialBytes);
        cleanupExpired(clock.instant());
        if (entries.containsKey(safeId)) {
            throw stateConflict();
        }
        ensureCapacity();
        Instant now = clock.instant();
        TransferProgress progress = snapshot(
                safeId,
                type,
                TransferStatus.RUNNING,
                totalBytes,
                initialBytes,
                now,
                now,
                now,
                null,
                null);
        ProgressEntry previous = entries.putIfAbsent(
                safeId, new ProgressEntry(progress, initialBytes, now));
        if (previous != null) {
            throw stateConflict();
        }
        notifyListeners(progress);
        return progress;
    }

    @Override
    public TransferProgress update(String transferId, long transferredBytes) {
        String safeId = requireTransferId(transferId);
        AtomicBoolean notify = new AtomicBoolean();
        ProgressEntry updated = entries.compute(safeId, (key, current) -> {
            if (current == null) {
                throw stateConflict();
            }
            TransferProgress previous = current.progress();
            if (previous.status() != TransferStatus.RUNNING
                    && previous.status() != TransferStatus.FINALIZING) {
                throw stateConflict();
            }
            if (transferredBytes < previous.transferredBytes()
                    || (previous.totalBytes() >= 0 && transferredBytes > previous.totalBytes())) {
                throw stateConflict();
            }
            Instant now = clock.instant();
            TransferProgress progress = snapshot(
                    previous.transferId(),
                    previous.type(),
                    previous.status(),
                    previous.totalBytes(),
                    transferredBytes,
                    previous.createdAt(),
                    previous.startedAt(),
                    now,
                    null,
                    previous.failureReason());
            boolean thresholdReached = transferredBytes - current.lastNotifiedBytes() >= notificationBytes;
            boolean intervalReached = !now.isBefore(
                    current.lastNotifiedAt().plus(notificationInterval));
            boolean totalReached = previous.totalBytes() >= 0
                    && transferredBytes == previous.totalBytes();
            if (thresholdReached || intervalReached || totalReached) {
                notify.set(true);
                return new ProgressEntry(progress, transferredBytes, now);
            }
            return new ProgressEntry(
                    progress, current.lastNotifiedBytes(), current.lastNotifiedAt());
        });
        if (notify.get()) {
            notifyListeners(updated.progress());
        }
        return updated.progress();
    }

    @Override
    public TransferProgress transition(
            String transferId,
            TransferStatus status,
            String safeFailureReason) {
        String safeId = requireTransferId(transferId);
        Objects.requireNonNull(status, "status 不能为空");
        ProgressEntry updated = entries.compute(safeId, (key, current) -> {
            if (current == null || !canTransition(current.progress().status(), status)) {
                throw stateConflict();
            }
            TransferProgress previous = current.progress();
            if (status == TransferStatus.COMPLETED
                    && previous.totalBytes() >= 0
                    && previous.transferredBytes() != previous.totalBytes()) {
                throw stateConflict();
            }
            Instant now = clock.instant();
            String failureReason = status == TransferStatus.FAILED
                    ? normalizeFailureReason(safeFailureReason)
                    : null;
            TransferProgress progress = snapshot(
                    previous.transferId(),
                    previous.type(),
                    status,
                    previous.totalBytes(),
                    previous.transferredBytes(),
                    previous.createdAt(),
                    previous.startedAt(),
                    now,
                    status.isTerminal() ? now : null,
                    failureReason);
            return new ProgressEntry(progress, previous.transferredBytes(), now);
        });
        notifyListeners(updated.progress());
        return updated.progress();
    }

    @Override
    public Optional<TransferProgress> find(String transferId) {
        String safeId = requireTransferId(transferId);
        cleanupExpired(clock.instant());
        ProgressEntry entry = entries.get(safeId);
        return entry == null ? Optional.empty() : Optional.of(entry.progress());
    }

    @Override
    public void remove(String transferId) {
        entries.remove(requireTransferId(transferId));
    }

    /**
     * 清理超过保留期的终态记录。
     *
     * @param now 当前时间
     */
    private void cleanupExpired(Instant now) {
        Instant cutoff = now.minus(retention);
        entries.entrySet().removeIf(entry -> {
            TransferProgress progress = entry.getValue().progress();
            return progress.status().isTerminal()
                    && progress.completedAt() != null
                    && !progress.completedAt().isAfter(cutoff);
        });
    }

    /**
     * 在创建新记录前执行有界容量控制。
     */
    private void ensureCapacity() {
        while (entries.size() >= maxEntries) {
            Optional<String> oldestTerminal = entries.entrySet().stream()
                    .filter(entry -> entry.getValue().progress().status().isTerminal())
                    .min(Comparator.comparing(entry -> entry.getValue().progress().completedAt()))
                    .map(java.util.Map.Entry::getKey);
            if (oldestTerminal.isEmpty()) {
                throw FileException.of(FileErrorCode.TRANSFER_FAILED);
            }
            entries.remove(oldestTerminal.get());
        }
    }

    /**
     * 构建派生速度和预计剩余时间后的不可变快照。
     */
    private TransferProgress snapshot(
            String transferId,
            TransferType type,
            TransferStatus status,
            long totalBytes,
            long transferredBytes,
            Instant createdAt,
            Instant startedAt,
            Instant updatedAt,
            Instant completedAt,
            String failureReason) {
        Duration elapsed = Duration.between(startedAt, updatedAt);
        double elapsedSeconds = elapsed.isNegative()
                ? 0
                : elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0;
        long bytesPerSecond = elapsedSeconds == 0
                ? 0
                : (long) (transferredBytes / elapsedSeconds);
        double percentage = totalBytes < 0
                ? -1
                : totalBytes == 0 ? 100 : Math.min(100, transferredBytes * 100.0 / totalBytes);
        Duration remaining = null;
        if (totalBytes >= 0 && bytesPerSecond > 0 && transferredBytes < totalBytes) {
            long seconds = (long) Math.ceil((totalBytes - transferredBytes) / (double) bytesPerSecond);
            remaining = Duration.ofSeconds(seconds);
        }
        return new TransferProgress(
                transferId,
                type,
                status,
                totalBytes,
                transferredBytes,
                percentage,
                bytesPerSecond,
                remaining,
                createdAt,
                startedAt,
                updatedAt,
                completedAt,
                failureReason);
    }

    /**
     * 判断状态转换是否合法。
     */
    private boolean canTransition(TransferStatus current, TransferStatus target) {
        if (current == target || current.isTerminal()) {
            return false;
        }
        return switch (current) {
            case CREATED -> target == TransferStatus.RUNNING
                    || target == TransferStatus.CANCELLED
                    || target == TransferStatus.EXPIRED;
            case RUNNING -> target == TransferStatus.PAUSED
                    || target == TransferStatus.FINALIZING
                    || target.isTerminal();
            case PAUSED -> target == TransferStatus.RUNNING
                    || target == TransferStatus.FINALIZING
                    || target.isTerminal();
            case FINALIZING -> target == TransferStatus.COMPLETED
                    || target == TransferStatus.FAILED;
            case COMPLETED, FAILED, CANCELLED, EXPIRED -> false;
        };
    }

    /**
     * 隔离通知所有进度监听器。
     */
    private void notifyListeners(TransferProgress progress) {
        for (TransferProgressListener listener : listeners) {
            try {
                listener.onProgress(progress);
            } catch (RuntimeException exception) {
                log.warn("传输进度监听器执行失败，transferId={}", progress.transferId(), exception);
            }
        }
    }

    /**
     * 校验传输编号只包含安全字符。
     */
    private String requireTransferId(String transferId) {
        if (transferId == null || !TRANSFER_ID_PATTERN.matcher(transferId).matches()) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "transferId");
        }
        return transferId;
    }

    /**
     * 校验总量与初始进度。
     */
    private void validateByteCounts(long totalBytes, long initialBytes) {
        if (totalBytes < -1 || initialBytes < 0
                || (totalBytes >= 0 && initialBytes > totalBytes)) {
            throw FileException.of(FileErrorCode.PARAMETER_INVALID, "transfer bytes");
        }
    }

    /**
     * 规范化失败原因，避免传播空值和过长诊断信息。
     */
    private String normalizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "传输失败";
        }
        String normalized = failureReason.trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    /**
     * 创建统一状态冲突异常。
     */
    private FileException stateConflict() {
        return FileException.of(FileErrorCode.RESUMABLE_STATE_CONFLICT, "进度状态已变化");
    }

    /**
     * 进度及通知采样位置。
     *
     * @param progress 可信进度
     * @param lastNotifiedBytes 最近通知字节数
     * @param lastNotifiedAt 最近通知时间
     */
    private record ProgressEntry(
            TransferProgress progress,
            long lastNotifiedBytes,
            Instant lastNotifiedAt) {
    }
}
