package uk.gov.justice.laa.dstew.access.massgenerator.job;

import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JobRepository {

  private final GenerationJobJpaRepository jpaRepository;

  @Transactional
  public void save(GenerationJob job) {
    GenerationJobEntity entity = toEntity(job);
    jpaRepository.save(entity);
  }

  public GenerationJob findById(String jobId) {
    return jpaRepository.findById(jobId).map(this::toModel).orElse(null);
  }

  public Map<String, GenerationJob> findAll() {
    Map<String, GenerationJob> result = new LinkedHashMap<>();
    jpaRepository.findAllByOrderByCreatedAtDesc().forEach(e -> result.put(e.getId(), toModel(e)));
    return result;
  }

  private GenerationJobEntity toEntity(GenerationJob job) {
    return GenerationJobEntity.builder()
        .id(job.getJobId())
        .status(job.getStatus())
        .targetCount(job.getTargetCount())
        .processedCount(job.getProcessedCount())
        .decidedCount(job.getDecidedCount())
        .errorCount(job.getErrorCount())
        .cleanupRequested(job.isCleanupRequested())
        .startedAt(job.getStartedAt())
        .completedAt(job.getCompletedAt())
        .errorMessage(job.getErrorMessage())
        .throughput(job.getThroughput())
        .createdAt(job.getStartedAt() != null ? job.getStartedAt() : java.time.Instant.now())
        .build();
  }

  private GenerationJob toModel(GenerationJobEntity entity) {
    return GenerationJob.builder()
        .jobId(entity.getId())
        .status(entity.getStatus())
        .targetCount(entity.getTargetCount())
        .processedCount(entity.getProcessedCount())
        .decidedCount(entity.getDecidedCount())
        .errorCount(entity.getErrorCount())
        .cleanupRequested(entity.isCleanupRequested())
        .startedAt(entity.getStartedAt())
        .completedAt(entity.getCompletedAt())
        .errorMessage(entity.getErrorMessage())
        .throughput(entity.getThroughput())
        .build();
  }
}
