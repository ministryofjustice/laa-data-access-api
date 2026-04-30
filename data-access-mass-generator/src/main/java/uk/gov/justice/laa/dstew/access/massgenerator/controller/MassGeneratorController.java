package uk.gov.justice.laa.dstew.access.massgenerator.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.massgenerator.job.GenerationJob;
import uk.gov.justice.laa.dstew.access.massgenerator.job.JobRepository;
import uk.gov.justice.laa.dstew.access.massgenerator.service.AsyncMassGeneratorService;
import uk.gov.justice.laa.dstew.access.massgenerator.service.DataCleanupService;

@Slf4j
@RestController
@RequestMapping("/api/mass-generator")
@RequiredArgsConstructor
public class MassGeneratorController {

  private final AsyncMassGeneratorService generatorService;
  private final JobRepository jobRepository;
  private final DataCleanupService cleanupService;

  @PostMapping("/generate")
  public ResponseEntity<Map<String, String>> startGeneration(
      @RequestParam(defaultValue = "1000") int count,
      @RequestParam(defaultValue = "false") boolean cleanup) {
    log.info("Received generation request: count={}, cleanup={}", count, cleanup);

    if (cleanup) {
      log.info("Running cleanup before generation...");
      cleanupService.cleanupAllTestData();
      log.info("Cleanup complete");
    }

    String jobId = generatorService.createJob(count, cleanup);
    generatorService.generateDataAsync(jobId, count);

    return ResponseEntity.accepted()
        .body(
            Map.of(
                "jobId",
                jobId,
                "message",
                "Generation job started",
                "statusUrl",
                "/api/mass-generator/jobs/" + jobId));
  }

  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<GenerationJob> getJobStatus(@PathVariable String jobId) {
    GenerationJob job = jobRepository.findById(jobId);
    if (job == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(job);
  }

  @GetMapping("/jobs")
  public ResponseEntity<Map<String, GenerationJob>> getAllJobs() {
    return ResponseEntity.ok(jobRepository.findAll());
  }

  @DeleteMapping("/jobs/{jobId}")
  public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
    generatorService.cancelJob(jobId);
    return ResponseEntity.noContent().build();
  }
}
