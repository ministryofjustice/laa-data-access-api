package uk.gov.justice.laa.dstew.access.massgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullCertificateGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullJsonGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullMeritsDecisionGenerator;
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

@Component
public class MassDataGeneratorRunner implements CommandLineRunner {

  private static final int DEFAULT_COUNT = 300000;
  private static final int BATCH_SIZE = 500;
  private static final double DECISION_RATE = 0.4;

  @Autowired private PersistedDataGenerator persistedDataGenerator;

  @Autowired private ObjectMapper objectMapper;

  private final FullMeritsDecisionGenerator meritsDecisionGenerator =
      new FullMeritsDecisionGenerator();
  private final FullCertificateGenerator certificateGenerator = new FullCertificateGenerator();

  @Override
  public void run(String... args) {
    int count = parseCount(args);

    long startTime = System.currentTimeMillis();
    System.out.printf("Mass generation started at %s%n", Instant.now());

    Faker faker = new Faker();

    // Pre-generate pools using random variants in one saveAllAndFlush each (Option D)
    List<CaseworkerEntity> caseworkers =
        persistedDataGenerator.createAndPersistMultipleRandom(CaseworkerGenerator.class, 100);
    System.out.printf("Created %d caseworkers%n", caseworkers.size());

    List<IndividualEntity> individuals =
        persistedDataGenerator.createAndPersistMultipleRandom(
            IndividualEntityGenerator.class, 1000);
    System.out.printf("Created %d individuals%n", individuals.size());

    // Store IDs only — the in-memory entities will be detached after flushAndClear()
    List<UUID> individualIds = individuals.stream().map(IndividualEntity::getId).toList();

    System.out.printf("Generating %d application records...%n", count);

    CategoryOfLawTypeConvertor colConvertor = new CategoryOfLawTypeConvertor();
    MatterTypeConvertor mtConvertor = new MatterTypeConvertor();

    int decidedCount = 0;

    for (int i = 0; i < count; i++) {

      // 1. Generate full rich application content
      ApplicationContent content = new FullJsonGenerator().createDefault();

      // 2. Pick a random caseworker and individual ID from the pre-generated pools.
      CaseworkerEntity cw = caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
      UUID selectedIndividualId =
          individualIds.get(faker.number().numberBetween(0, individualIds.size()));

      // 3. Derive entity-level columns from the content (mirrors ApplicationContentParserService)
      Proceeding lead =
          content.getProceedings().stream()
              .filter(p -> Boolean.TRUE.equals(p.getLeadProceeding()))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("No lead proceeding found in generated content"));

      CategoryOfLaw col = colConvertor.lenientEnumConversion(lead.getCategoryOfLaw());
      MatterType mt = mtConvertor.lenientEnumConversion(lead.getMatterType());
      boolean udf =
          content.getProceedings().stream()
              .anyMatch(p -> Boolean.TRUE.equals(p.getUsedDelegatedFunctions()));

      // 4. Decide upfront whether this application will have a decision
      final boolean hasDecision = faker.number().randomDouble(2, 0, 1) < DECISION_RATE;
      final DecisionStatus overallDecision =
          !hasDecision
              ? null
              : faker
                  .options()
                  .option(
                      DecisionStatus.REFUSED, // 50 %
                      DecisionStatus.REFUSED,
                      DecisionStatus.REFUSED,
                      DecisionStatus.GRANTED, // 30 %
                      DecisionStatus.GRANTED,
                      DecisionStatus.REFUSED // 20 %
                      );

      if (hasDecision) {
        decidedCount++;
      }

      // 5. Build proceedings in memory, each with a MeritsDecisionEntity already attached
      // (only when a decision exists). CascadeType.ALL on ApplicationEntity.proceedings and
      // ProceedingEntity.meritsDecision means a single createAndPersist call persists everything.
      Set<ProceedingEntity> proceedingSet = new HashSet<>();
      for (Proceeding p : content.getProceedings()) {
        MeritsDecisionEntity merit = hasDecision ? meritsDecisionGenerator.createDefault() : null;
        proceedingSet.add(
            DataGenerator.createDefault(
                ProceedingsEntityGenerator.class,
                b ->
                    b.applyProceedingId(p.getId())
                        .description(p.getDescription())
                        .isLead(Boolean.TRUE.equals(p.getLeadProceeding()))
                        .createdBy("mass-generator")
                        .updatedBy("mass-generator")
                        .proceedingContent(objectMapper.convertValue(p, Map.class))
                        .meritsDecision(merit)));
      }

      // 6. Build DecisionEntity in memory (when applicable)
      DecisionEntity decision =
          hasDecision
              ? DataGenerator.createDefault(
                  DecisionEntityGenerator.class, b -> b.overallDecision(overallDecision))
              : null;

      // 7. Persist the full aggregate in one transaction — the EntityManager is used to load
      // the individual as a managed entity within that same transaction, avoiding
      // "Detached entity passed to persist" on the CascadeType.PERSIST individuals collection.
      final CategoryOfLaw finalCol = col;
      final MatterType finalMt = mt;
      final boolean finalUdf = udf;
      final Set<ProceedingEntity> finalProceedingSet = proceedingSet;
      final DecisionEntity finalDecision = decision;
      ApplicationEntity app =
          persistedDataGenerator.createAndPersistInTransaction(
              ApplicationEntityGenerator.class,
              em -> {
                IndividualEntity managedIndiv =
                    em.find(IndividualEntity.class, selectedIndividualId);
                return b ->
                    b.applyApplicationId(content.getId())
                        .laaReference(content.getLaaReference())
                        .submittedAt(Instant.parse(content.getSubmittedAt()))
                        .officeCode(
                            content.getOffice() != null ? content.getOffice().getCode() : null)
                        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .categoryOfLaw(finalCol)
                        .matterType(finalMt)
                        .usedDelegatedFunctions(finalUdf)
                        .applicationContent(objectMapper.convertValue(content, Map.class))
                        .caseworker(cw)
                        .individuals(new HashSet<>(Set.of(managedIndiv)))
                        .proceedings(finalProceedingSet)
                        .decision(finalDecision)
                        .isAutoGranted(hasDecision && overallDecision == DecisionStatus.GRANTED);
              });

      // 8. Certificate (GRANTED only) — persisted separately as it is not part of the aggregate
      if (hasDecision && overallDecision == DecisionStatus.GRANTED) {
        CertificateEntity cert =
            certificateGenerator.createDefault(b -> b.applicationId(app.getId()));
        persistedDataGenerator.persist(FullCertificateGenerator.class, cert);
      }

      // 9. Flush + clear Hibernate session every BATCH_SIZE applications
      if ((i + 1) % BATCH_SIZE == 0) {
        persistedDataGenerator.flushAndClear();
        System.out.printf("Persisted %d / %d applications%n", i + 1, count);
      }
    }

    // flush any remaining partial batch
    persistedDataGenerator.flushAndClear();

    long elapsedMs = System.currentTimeMillis() - startTime;
    long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs);
    long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60;
    double recordsPerSecond = count / (elapsedMs / 1000.0);

    System.out.println("========================================");
    System.out.println("       Mass Generation Report");
    System.out.println("========================================");
    System.out.printf("  Records generated  : %d%n", count);
    System.out.printf(
        "  Decided applications: %d (%.0f%%)%n", decidedCount, 100.0 * decidedCount / count);
    System.out.printf("  Total time         : %d min %02d sec%n", minutes, seconds);
    System.out.printf("  Throughput         : %.1f records/sec%n", recordsPerSecond);
    System.out.println("========================================");
  }

  private int parseCount(String[] args) {
    if (args != null && args.length > 0) {
      try {
        return Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        System.err.printf(
            "Invalid count argument '%s', using default %d%n", args[0], DEFAULT_COUNT);
      }
    }
    return DEFAULT_COUNT;
  }
}
