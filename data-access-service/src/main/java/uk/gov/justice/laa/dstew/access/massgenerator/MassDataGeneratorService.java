package uk.gov.justice.laa.dstew.access.massgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Qualifier;
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
import uk.gov.justice.laa.dstew.access.massgenerator.model.MassDataGenerationRequest;
import uk.gov.justice.laa.dstew.access.massgenerator.model.MassDataGenerationResponse;
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

/**
 * Service for generating mass test data including applications, decisions, certificates, and linked
 * relationships.
 */
@Service
@Slf4j
public class MassDataGeneratorService {

  private final PersistedDataGenerator persistedDataGenerator;
  private final LinkedIndividualWriter linkedIndividualWriter;
  private final ObjectMapper objectMapper;

  public MassDataGeneratorService(
      @Qualifier("massGeneratorPersistedDataGenerator")
          PersistedDataGenerator persistedDataGenerator,
      @Qualifier("massGeneratorLinkedIndividualWriter")
          LinkedIndividualWriter linkedIndividualWriter,
      ObjectMapper objectMapper) {
    this.persistedDataGenerator = persistedDataGenerator;
    this.linkedIndividualWriter = linkedIndividualWriter;
    this.objectMapper = objectMapper;
  }

  // Non-Spring managed generators - created on demand
  private FullMeritsDecisionGenerator meritsDecisionGenerator;
  private FullCertificateGenerator certificateGenerator;
  private FullJsonGenerator jsonGenerator;

  private void initGenerators() {
    if (meritsDecisionGenerator == null) {
      meritsDecisionGenerator = new FullMeritsDecisionGenerator();
      certificateGenerator = new FullCertificateGenerator();
      jsonGenerator = new FullJsonGenerator();
    }
  }

  /**
   * Generate mass test data.
   *
   * @param request the generation request containing count and configuration
   * @return response with generation statistics
   */
  @Transactional
  public MassDataGenerationResponse generateMassData(MassDataGenerationRequest request) {
    initGenerators();

    int count = request.getCount();
    int batchSize = request.getBatchSize();
    double decisionRate = request.getDecisionRate();
    double linkRate = request.getLinkRate();

    long startTime = System.currentTimeMillis();
    log.info("Mass generation started at {} for {} records", Instant.now(), count);

    Faker faker = new Faker();

    // Pre-generate pools using random variants in one saveAllAndFlush each
    List<CaseworkerEntity> caseworkers =
        persistedDataGenerator.createAndPersistMultipleRandom(CaseworkerGenerator.class, 100);
    log.info("Created {} caseworkers", caseworkers.size());

    List<IndividualEntity> individuals =
        persistedDataGenerator.createAndPersistMultipleRandom(
            IndividualEntityGenerator.class, Math.min(count, 100));
    log.info("Created {} individuals", individuals.size());

    // Flush pre-generated pools before main generation loop
    persistedDataGenerator.flush();

    log.info("Generating {} application records...", count);

    CategoryOfLawTypeConvertor colConvertor = new CategoryOfLawTypeConvertor();
    MatterTypeConvertor mtConvertor = new MatterTypeConvertor();

    int decidedCount = 0;
    int linkedCount = 0;
    List<UUID> persistedAppIds = new ArrayList<>();
    // Buffer for (applicationId, individualId) pairs — flushed to DB at batch boundaries
    // after Hibernate has written the application rows.
    List<UUID[]> pendingIndividualLinks = new ArrayList<>();

    for (int i = 0; i < count; i++) {

      // 1. Generate full rich application content
      ApplicationContent content = jsonGenerator.createDefault();

      // 2. Pick a random caseworker and individual from the pre-generated pools
      CaseworkerEntity cw = caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
      IndividualEntity indiv = individuals.get(faker.number().numberBetween(0, individuals.size()));

      // 3. Derive entity-level columns from the content (mirrors ApplicationContentParserService)
      // Optimized: single pass instead of two stream operations
      Proceeding lead = null;
      boolean udfFound = false;
      for (Proceeding p : content.getProceedings()) {
        if (Boolean.TRUE.equals(p.getLeadProceeding())) {
          lead = p;
        }
        if (Boolean.TRUE.equals(p.getUsedDelegatedFunctions())) {
          udfFound = true;
        }
      }
      if (lead == null) {
        throw new IllegalStateException("No lead proceeding found in generated content");
      }

      CategoryOfLaw col = colConvertor.lenientEnumConversion(lead.getCategoryOfLaw());
      MatterType mt = mtConvertor.lenientEnumConversion(lead.getMatterType());
      final boolean udf = udfFound;

      // 4. Persist ApplicationEntity with all required columns populated
      ApplicationEntity app =
          persistedDataGenerator.createAndPersist(
              ApplicationEntityGenerator.class,
              b ->
                  b.applyApplicationId(content.getId())
                      .laaReference(content.getLaaReference())
                      .submittedAt(Instant.parse(content.getSubmittedAt()))
                      .officeCode(
                          content.getOffice() != null ? content.getOffice().getCode() : null)
                      .status(
                          faker
                              .options()
                              .option(
                                  ApplicationStatus.APPLICATION_IN_PROGRESS,
                                  ApplicationStatus.APPLICATION_SUBMITTED))
                      .categoryOfLaw(col)
                      .matterType(mt)
                      .usedDelegatedFunctions(udf)
                      .applicationContent(objectMapper.convertValue(content, Map.class))
                      .caseworker(cw));
      persistedAppIds.add(app.getId());

      // 4a. Buffer the individual link — will be flushed after Hibernate writes the app row
      pendingIndividualLinks.add(new UUID[] {app.getId(), indiv.getId()});

      // 5. Accumulate proceedings then persist as a single saveAllAndFlush
      List<ProceedingEntity> proceedingBatch = new ArrayList<>();
      for (Proceeding p : content.getProceedings()) {
        proceedingBatch.add(
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
      persistedDataGenerator.persist(ProceedingsEntityGenerator.class, proceedingBatch);

      // 6. Optionally generate a decision for this application
      if (faker.number().randomDouble(2, 0, 1) < decisionRate) {
        decidedCount++;

        DecisionStatus overallDecision =
            faker
                .options()
                .option(
                    DecisionStatus.REFUSED,
                    DecisionStatus.REFUSED,
                    DecisionStatus.REFUSED,
                    DecisionStatus.GRANTED,
                    DecisionStatus.GRANTED);

        // Re-attach proceedings and application in case the session was cleared mid-batch
        List<ProceedingEntity> attachedProceedings =
            persistedDataGenerator.reattach(proceedingBatch);
        ApplicationEntity attachedApp = persistedDataGenerator.reattach(List.of(app)).get(0);

        // 6a. One MeritsDecisionEntity per proceeding
        Set<MeritsDecisionEntity> merits = new LinkedHashSet<>();
        for (ProceedingEntity pe : attachedProceedings) {
          MeritsDecisionEntity merit = meritsDecisionGenerator.createDefault(b -> b.proceeding(pe));
          persistedDataGenerator.persist(FullMeritsDecisionGenerator.class, merit);
          merits.add(merit);
        }

        // 6b. DecisionEntity — persist without merits first
        DecisionEntity decision = DecisionEntity.builder().overallDecision(overallDecision).build();
        persistedDataGenerator.persist(DecisionEntityGenerator.class, decision);
        // Now attach the merits via merge
        decision.setMeritsDecisions(merits);
        persistedDataGenerator.mergeDecision(decision);

        // 6c. Certificate (GRANTED only)
        if (overallDecision == DecisionStatus.GRANTED) {
          CertificateEntity cert =
              certificateGenerator.createDefault(b -> b.applicationId(attachedApp.getId()));
          persistedDataGenerator.persist(FullCertificateGenerator.class, cert);
        }

        // 6d. Link decision back to application
        attachedApp.setDecision(decision);
        attachedApp.setIsAutoGranted(overallDecision == DecisionStatus.GRANTED);
        persistedDataGenerator.saveApplication(attachedApp);
      }

      // 7. Flush + clear Hibernate session every BATCH_SIZE applications
      if ((i + 1) % batchSize == 0) {
        // Flush Hibernate writes so application rows exist before the native FK insert
        persistedDataGenerator.flush();
        linkedIndividualWriter.linkAll(pendingIndividualLinks);
        pendingIndividualLinks.clear();
        persistedDataGenerator.clear();
        log.info("Persisted {} / {} applications", i + 1, count);
      }
    }

    // flush any remaining partial batch
    persistedDataGenerator.flush();
    linkedIndividualWriter.linkAll(pendingIndividualLinks);
    pendingIndividualLinks.clear();
    persistedDataGenerator.clear();

    // Link applications in batches to reduce query overhead
    log.info("Starting linking phase for {} applications...", persistedAppIds.size());
    int i = 0;
    List<List<UUID>> linkBatch = new ArrayList<>();

    while (i < persistedAppIds.size()) {
      if (faker.number().randomDouble(2, 0, 1) < linkRate && i + 1 < persistedAppIds.size()) {
        UUID leadId = persistedAppIds.get(i);
        i++;
        int associateCount = faker.number().numberBetween(1, 4);
        for (int j = 0; j < associateCount && i < persistedAppIds.size(); j++) {
          // Collect link pairs for batch processing
          linkBatch.add(List.of(leadId, persistedAppIds.get(i)));
          i++;
          linkedCount++;

          // Process in batches of 50 links to reduce memory and queries
          if (linkBatch.size() >= 50) {
            persistedDataGenerator.linkApplicationsBatch(linkBatch);
            persistedDataGenerator.flushAndClear();
            linkBatch.clear();
            log.info("Linked {} applications so far...", linkedCount);
          }
        }
      } else {
        i++;
      }
    }

    // Process any remaining links
    if (!linkBatch.isEmpty()) {
      persistedDataGenerator.linkApplicationsBatch(linkBatch);
      persistedDataGenerator.flushAndClear();
    }

    log.info("Linking phase completed: {} applications linked", linkedCount);

    long elapsedMs = System.currentTimeMillis() - startTime;
    long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs);
    long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60;
    double recordsPerSecond = count / (elapsedMs / 1000.0);

    log.info("========================================");
    log.info("       Mass Generation Report");
    log.info("========================================");
    log.info("  Records generated  : {}", count);
    log.info(
        "  Decided applications: {} ({}%)",
        decidedCount, String.format("%.0f", 100.0 * decidedCount / count));
    log.info("  Linked applications : {} pairs", linkedCount);
    log.info("  Total time         : {} min {:02d} sec", minutes, seconds);
    log.info("  Throughput         : {} records/sec", String.format("%.1f", recordsPerSecond));
    log.info("========================================");

    return MassDataGenerationResponse.builder()
        .recordsGenerated(count)
        .decidedCount(decidedCount)
        .linkedCount(linkedCount)
        .durationMillis(elapsedMs)
        .throughputRecordsPerSecond(recordsPerSecond)
        .build();
  }
}
