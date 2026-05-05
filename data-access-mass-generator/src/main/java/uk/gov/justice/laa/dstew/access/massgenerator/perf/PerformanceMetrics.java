package uk.gov.justice.laa.dstew.access.massgenerator.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe collector for per-operation latency samples (nanoseconds). Call {@link #record(long)}
 * from any thread; retrieve stats after all work completes.
 */
public class PerformanceMetrics {

  private final String operationName;
  private final List<Long> samples = Collections.synchronizedList(new ArrayList<>());
  private final AtomicLong errorCount = new AtomicLong(0);

  public PerformanceMetrics(String operationName) {
    this.operationName = operationName;
  }

  public void record(long nanos) {
    samples.add(nanos);
  }

  public void recordError() {
    errorCount.incrementAndGet();
  }

  public String getOperationName() {
    return operationName;
  }

  public int getRequestCount() {
    return samples.size();
  }

  public long getErrorCount() {
    return errorCount.get();
  }

  /** Mean latency in milliseconds. */
  public double meanMs() {
    return samples.isEmpty()
        ? 0
        : samples.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
  }

  /** Minimum latency in milliseconds. */
  public long minMs() {
    return samples.isEmpty()
        ? 0
        : samples.stream().mapToLong(Long::longValue).min().orElse(0) / 1_000_000;
  }

  /** Maximum latency in milliseconds. */
  public long maxMs() {
    return samples.isEmpty()
        ? 0
        : samples.stream().mapToLong(Long::longValue).max().orElse(0) / 1_000_000;
  }

  /** p50 (median) latency in milliseconds. */
  public long p50Ms() {
    return percentileMs(50);
  }

  /** p95 latency in milliseconds. */
  public long p95Ms() {
    return percentileMs(95);
  }

  /** p99 latency in milliseconds. */
  public long p99Ms() {
    return percentileMs(99);
  }

  private long percentileMs(int pct) {
    if (samples.isEmpty()) return 0;
    List<Long> sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    index = Math.max(0, Math.min(index, sorted.size() - 1));
    return sorted.get(index) / 1_000_000;
  }
}
