package uk.gov.justice.laa.dstew.access.massgenerator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.LinkedIndividualWriter;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullCertificateGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullJsonGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullMeritsDecisionGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.job.GenerationJob;
import uk.gov.justice.laa.dstew.access.massgenerator.job.JobRepository;
import uk.gov.justice.laa.dstew.access.massgenerator.job.JobStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMassGeneratorService {

  private static final int BATCH_SIZE = 500;
  private static final double DECISION_RATE = 0.4;

  private final PersistedDataGenerator persistedDataGenerator;
  private final LinkedIndividualWriter linkedIndividualWriter;
  private final ObjectMapper objectMapper;
  private final JobRepository jobRepository;

  private final FullMeritsDecisionGenerator meritsDecisionGenerator =
      new FullMeritsDecisionGenerator();
  private final FullCertificateGenerator certificateGenerator = new FullCertificateGenerator();

  @Async("massGeneratorExecutor")
  public void generateDataAsync(String jobId, int count) {
    GenerationJob job = jobRepository.findById(jobId);
    if (job == null) {
      log.error("Job {} not found", jobId);
      return;
    }

    try {
      job.setStatus(JobStatus.RUNNING);
      job.setStartedAt(Instant.now());
      jobRepository.save(job);

      long startTime = System.currentTimeMillis();
      log.info("Job {}: Mass generation started", jobId);

      Faker faker = new Faker();

      List<CaseworkerEntity> caseworkers =
          persistedDataGenerator.createAndPersistMultipleRandom(CaseworkerGenerator.class, 100);
      List<IndividualEntity> individuals =
          persistedDataGenerator.createAndPersistMultipleRandom(
              IndividualEntityGenerator.class, 1000);

      CategoryOfLawTypeConvertor colConvertor = new CategoryOfLawTypeConvertor();
      MatterTypeConvertor mtConvertor = new MatterTypeConvertor();

      // Reuse a single generator instance across all iterations to avoid object churn
      FullJsonGenerator jsonGenerator = new FullJsonGenerator();

      int decidedCount = 0;
      int errorCount = 0;

      // Phase-grouped accumulators — populated per record, flushed together at batch boundaries
      // so Hibernate can form dense same-table JDBC batches.
      List<ApplicationEntity> appBatch = new ArrayList<>(BATCH_SIZE);
      List<ProceedingEntity> procBatch = new ArrayList<>(BATCH_SIZE * 3);
      List<MeritsDecisionEntity> meritsBatch = new ArrayList<>(BATCH_SIZE);
      List<DecisionEntity> decisionBatch = new ArrayList<>(BATCH_SIZE);
      List<CertificateEntity> certBatch = new ArrayList<>(BATCH_SIZE);

      for (int i = 0; i < count; i++) {

        try {
          ApplicationContent content = jsonGenerator.createDefault();
          CaseworkerEntity cw =
              caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
          IndividualEntity indiv =
              individuals.get(faker.number().numberBetween(0, individuals.size()));

          // Single pass over proceedings to find lead and check delegated functions
          Proceeding lead = null;
          boolean udf = false;
          for (Proceeding p : content.getProceedings()) {
            if (Boolean.TRUE.equals(p.getLeadProceeding())) {
              lead = p;
            }
            if (Boolean.TRUE.equals(p.getUsedDelegatedFunctions())) {
              udf = true;
            }
          }
          if (lead == null) {
            throw new IllegalStateException("No lead proceeding");
          }

          final CategoryOfLaw col = colConvertor.lenientEnumConversion(lead.getCategoryOfLaw());
          final MatterType mt = mtConvertor.lenientEnumConversion(lead.getMatterType());
          final boolean udfFinal = udf;

          ApplicationEntity app =
              DataGenerator.createDefault(
                  ApplicationEntityGenerator.class,
                  b ->
                      b.applyApplicationId(content.getId())
                          .laaReference(content.getLaaReference())
                          .submittedAt(Instant.parse(content.getSubmittedAt()))
                          .officeCode(
                              content.getOffice() != null ? content.getOffice().getCode() : null)
                          .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                          .categoryOfLaw(col)
                          .matterType(mt)
                          .usedDelegatedFunctions(udfFinal)
                          .applicationContent(objectMapper.convertValue(content, Map.class))
                          .caseworker(cw));
          appBatch.add(app);

          linkedIndividualWriter.linkDeferred(app.getId(), indiv.getId());

          List<ProceedingEntity> recordProceedings = new ArrayList<>();
          for (Proceeding p : content.getProceedings()) {
            recordProceedings.add(
                DataGenerator.createDefault(
                    ProceedingsEntityGenerator.class,
                    b ->
                        b.applicationId(app.getId())
                            .applyProceedingId(p.getId())
                            .description(p.getDescription())
                            .isLead(Boolean.TRUE.equals(p.getLeadProceeding()))
                            .createdBy("mass-generator")
                            .updatedBy("mass-generator")
                            .proceedingContent(objectMapper.convertValue(p, Map.class))));
          }
          procBatch.addAll(recordProceedings);

          if (faker.number().randomDouble(2, 0, 1) < DECISION_RATE) {
            decidedCount++;
            DecisionStatus overallDecision =
                faker
                    .options()
                    .option(
                        DecisionStatus.REFUSED,
                        DecisionStatus.REFUSED,
                        DecisionStatus.REFUSED,
                        DecisionStatus.GRANTED,
                        DecisionStatus.GRANTED,
                        DecisionStatus.REFUSED);

            Set<MeritsDecisionEntity> merits = new LinkedHashSet<>();
            for (ProceedingEntity pe : recordProceedings) {
              MeritsDecisionEntity merit =
                  meritsDecisionGenerator.createDefault(b -> b.proceeding(pe));
              merits.add(merit);
              meritsBatch.add(merit);
            }

            DecisionEntity decision =
                DecisionEntity.builder().overallDecision(overallDecision).build();
            decision.setMeritsDecisions(merits);
            decisionBatch.add(decision);

            if (overallDecision == DecisionStatus.GRANTED) {
              CertificateEntity cert =
                  certificateGenerator.createDefault(b -> b.applicationId(app.getId()));
              certBatch.add(cert);
            }

            app.setDecision(decision);
            app.setIsAutoGranted(overallDecision == DecisionStatus.GRANTED);
          }

        } catch (Exception recordEx) {
          errorCount++;
          log.error(
              "Job {}: Error generating record {} - {}", jobId, i, recordEx.getMessage(), recordEx);
        }

        if ((i + 1) % BATCH_SIZE == 0) {
          // Persist phase-by-phase so Hibernate batches same-table INSERTs together
          persistedDataGenerator.persistAllNoFlush(ApplicationEntityGenerator.class, appBatch);
          persistedDataGenerator.persistAllNoFlush(ProceedingsEntityGenerator.class, procBatch);
          persistedDataGenerator.persistAllNoFlush(FullMeritsDecisionGenerator.class, meritsBatch);
          persistedDataGenerator.persistAllNoFlush(DecisionEntityGenerator.class, decisionBatch);
          persistedDataGenerator.persistAllNoFlush(FullCertificateGenerator.class, certBatch);

          persistedDataGenerator.flushAndClear();
          linkedIndividualWriter.flushDeferred();

          appBatch.clear();
          procBatch.clear();
          meritsBatch.clear();
          decisionBatch.clear();
          certBatch.clear();

          job.setProcessedCount(i + 1);
          job.setDecidedCount(decidedCount);
          job.setErrorCount(errorCount);
          jobRepository.save(job);
          log.info("Job {}: {} / {} applications", jobId, i + 1, count);

          // Check for cancellation at batch boundaries
          GenerationJob currentJob = jobRepository.findById(jobId);
          if (currentJob.getStatus() == JobStatus.CANCELLED) {
            log.info("Job {}: Cancelled at {} records", jobId, i + 1);
            return;
          }
        }
      }

      // Flush any remaining records that didn't fill a full batch
      if (!appBatch.isEmpty()) {
        persistedDataGenerator.persistAllNoFlush(ApplicationEntityGenerator.class, appBatch);
        persistedDataGenerator.persistAllNoFlush(ProceedingsEntityGenerator.class, procBatch);
        persistedDataGenerator.persistAllNoFlush(FullMeritsDecisionGenerator.class, meritsBatch);
        persistedDataGenerator.persistAllNoFlush(DecisionEntityGenerator.class, decisionBatch);
        persistedDataGenerator.persistAllNoFlush(FullCertificateGenerator.class, certBatch);
      }

      persistedDataGenerator.flushAndClear();
      linkedIndividualWriter.flushDeferred();

      long elapsedMs = System.currentTimeMillis() - startTime;
      double throughput = count / (elapsedMs / 1000.0);

      job.setStatus(JobStatus.COMPLETED);
      job.setCompletedAt(Instant.now());
      job.setProcessedCount(count);
      job.setDecidedCount(decidedCount);
      job.setThroughput(throughput);
      job.setErrorCount(errorCount);
      jobRepository.save(job);

      log.info("Job {}: COMPLETED - {} records, {} rec/sec", jobId, count, throughput);

    } catch (Exception e) {
      log.error("Job {}: FAILED - {}", jobId, e.getMessage(), e);
      job.setStatus(JobStatus.FAILED);
      job.setCompletedAt(Instant.now());
      job.setErrorMessage(e.getMessage());
      jobRepository.save(job);
    }
  }

  public String createJob(int count, boolean cleanup) {
    String jobId = UUID.randomUUID().toString();
    GenerationJob job =
        GenerationJob.builder()
            .jobId(jobId)
            .status(JobStatus.QUEUED)
            .targetCount(count)
            .processedCount(0)
            .decidedCount(0)
            .cleanupRequested(cleanup)
            .build();

    jobRepository.save(job);
    log.info("Created job {} for {} records (cleanup: {})", jobId, count, cleanup);
    return jobId;
  }

  public void cancelJob(String jobId) {
    GenerationJob job = jobRepository.findById(jobId);
    if (job != null
        && (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING)) {
      job.setStatus(JobStatus.CANCELLED);
      job.setCompletedAt(Instant.now());
      jobRepository.save(job);
      log.info("Cancelled job {}", jobId);
    }
  }
}
