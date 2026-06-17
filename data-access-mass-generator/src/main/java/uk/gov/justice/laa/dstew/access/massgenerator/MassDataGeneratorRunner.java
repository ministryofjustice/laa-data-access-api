package uk.gov.justice.laa.dstew.access.massgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
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
import uk.gov.justice.laa.dstew.access.massgenerator.generator.LinkedIndividualWriter;
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

  private static final double LINK_RATE = 0.1; // ~10% of applications will form a linked group

  // Pool of individuals shared across applications. In production one client is associated with
  // many applications; creating a fresh individual per application (1:1) produces artificially
  // uniform cardinality in linked_individuals and inflates individuals row counts. Pool size is
  // tuned so that on average ~10 applications share an individual.
  private static final int INDIVIDUAL_POOL_DIVISOR = 10; // target apps per individual
  private static final int INDIVIDUAL_POOL_MIN = 100;
  private static final int INDIVIDUAL_POOL_MAX = 20_000; // cap to keep memory bounded

  // Weighted status distribution — approx. production shape (most applications progress past
  // IN_PROGRESS). Repeat-entry weighting is cheap and readable; faker.options() picks uniformly
  // across the varargs, so duplicates increase that value's probability.
  private static final ApplicationStatus[] STATUS_WEIGHTED =
      new ApplicationStatus[] {
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED,
        ApplicationStatus.APPLICATION_SUBMITTED, // 9/10 ≈ 90%
        ApplicationStatus.APPLICATION_IN_PROGRESS // 1/10 ≈ 10%
      };

  @Autowired private PersistedDataGenerator persistedDataGenerator;

  @Autowired private LinkedIndividualWriter linkedIndividualWriter;

  @Autowired private ObjectMapper objectMapper;

  private final FullMeritsDecisionGenerator meritsDecisionGenerator =
      new FullMeritsDecisionGenerator();
  private final FullCertificateGenerator certificateGenerator = new FullCertificateGenerator();
  private final FullJsonGenerator jsonGenerator = new FullJsonGenerator();

  @Override
  public void run(String... args) {
    int count = parseCount(args);
    long startTime = System.currentTimeMillis();
    System.out.printf("Mass generation started at %s%n", Instant.now());

    Faker faker = new Faker();

    List<CaseworkerEntity> caseworkers = generateCaseworkers();
    List<UUID> individualPool = generateIndividualPool(faker, count);

    System.out.printf("Generating %d application records...%n", count);

    CategoryOfLawTypeConvertor colConvertor = new CategoryOfLawTypeConvertor();
    MatterTypeConvertor mtConvertor = new MatterTypeConvertor();

    int decidedCount = 0;
    int linkedCount = 0;
    List<UUID> persistedAppIds = new ArrayList<>();
    List<UUID[]> pendingLinks = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      ApplicationContent content = jsonGenerator.createDefault();
      CaseworkerEntity caseworker =
          caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
      UUID individualId =
          individualPool.get(faker.number().numberBetween(0, individualPool.size()));

      Proceeding leadProceeding = extractLeadProceeding(content);
      CategoryOfLaw categoryOfLaw =
          colConvertor.lenientEnumConversion(leadProceeding.getCategoryOfLawEnum());
      MatterType matterType = mtConvertor.lenientEnumConversion(leadProceeding.getMatterTypeEnum());
      boolean hasUsedDelegatedFunctions = hasUsedDelegatedFunctions(content);

      boolean hasDecision = faker.number().randomDouble(2, 0, 1) < DECISION_RATE;
      DecisionStatus overallDecision = hasDecision ? pickDecisionStatus(faker) : null;

      if (hasDecision) decidedCount++;

      Set<ProceedingEntity> proceedingSet = buildProceedings(content, hasDecision);
      DecisionEntity decision = hasDecision ? buildDecision(overallDecision) : null;

      ApplicationEntity app =
          persistApplication(
              content,
              caseworker,
              categoryOfLaw,
              matterType,
              hasUsedDelegatedFunctions,
              faker,
              proceedingSet,
              decision,
              hasDecision,
              overallDecision);

      persistedAppIds.add(app.getId());
      pendingLinks.add(new UUID[] {app.getId(), individualId});

      if (hasDecision && overallDecision == DecisionStatus.GRANTED) {
        persistCertificate(app);
      }

      if ((i + 1) % BATCH_SIZE == 0) {
        flushBatch(pendingLinks);
        System.out.printf("Persisted %d / %d applications%n", i + 1, count);
      }
    }

    flushBatch(pendingLinks);

    linkedCount = linkApplications(faker, persistedAppIds);

    printReport(count, decidedCount, linkedCount, startTime);
  }

  private List<CaseworkerEntity> generateCaseworkers() {
    List<CaseworkerEntity> caseworkers =
        persistedDataGenerator.createAndPersistMultipleRandom(CaseworkerGenerator.class, 100);
    System.out.printf("Created %d caseworkers%n", caseworkers.size());
    return caseworkers;
  }

  private List<UUID> generateIndividualPool(Faker faker, int count) {
    int poolSize =
        Math.min(
            INDIVIDUAL_POOL_MAX, Math.max(INDIVIDUAL_POOL_MIN, count / INDIVIDUAL_POOL_DIVISOR));
    System.out.printf("Building individual pool of %d entries...%n", poolSize);
    List<UUID> pool = new ArrayList<>(poolSize);
    for (int k = 0; k < poolSize; k++) {
      IndividualEntity indiv =
          persistedDataGenerator.createAndPersist(
              IndividualEntityGenerator.class,
              b ->
                  b.lastName(faker.name().lastName())
                      .firstName(faker.name().firstName())
                      .dateOfBirth(faker.timeAndDate().birthday())
                      .individualContent(Map.of("test", faker.text().text(10, 35, true))));
      pool.add(indiv.getId());
      if ((k + 1) % BATCH_SIZE == 0) {
        persistedDataGenerator.flushAndClear();
      }
    }
    persistedDataGenerator.flushAndClear();
    System.out.printf("Created %d individuals%n", pool.size());
    return pool;
  }

  private Proceeding extractLeadProceeding(ApplicationContent content) {
    return content.getProceedings().stream()
        .filter(p -> Boolean.TRUE.equals(p.getLeadProceeding()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("No lead proceeding found in generated content"));
  }

  private boolean hasUsedDelegatedFunctions(ApplicationContent content) {
    return content.getProceedings().stream()
        .anyMatch(p -> Boolean.TRUE.equals(p.getUsedDelegatedFunctions()));
  }

  private ApplicationStatus pickApplicationStatus(Faker faker) {
    return faker.options().option(STATUS_WEIGHTED);
  }

  private DecisionStatus pickDecisionStatus(Faker faker) {
    return faker
        .options()
        .option(
            DecisionStatus.REFUSED,
            DecisionStatus.REFUSED,
            DecisionStatus.REFUSED,
            DecisionStatus.GRANTED,
            DecisionStatus.GRANTED,
            DecisionStatus.REFUSED);
  }

  private Set<ProceedingEntity> buildProceedings(ApplicationContent content, boolean hasDecision) {
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
    return proceedingSet;
  }

  private DecisionEntity buildDecision(DecisionStatus overallDecision) {
    return DataGenerator.createDefault(
        DecisionEntityGenerator.class, b -> b.overallDecision(overallDecision));
  }

  private ApplicationEntity persistApplication(
      ApplicationContent content,
      CaseworkerEntity caseworker,
      CategoryOfLaw categoryOfLaw,
      MatterType matterType,
      boolean hasUsedDelegatedFunctions,
      Faker faker,
      Set<ProceedingEntity> proceedingSet,
      DecisionEntity decision,
      boolean hasDecision,
      DecisionStatus overallDecision) {
    return persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        b ->
            b.applyApplicationId(content.getId())
                .laaReference(content.getLaaReference())
                .submittedAt(Instant.parse(content.getSubmittedAt()))
                .officeCode(content.getOffice() != null ? content.getOffice().getCode() : null)
                .status(pickApplicationStatus(faker))
                .categoryOfLaw(categoryOfLaw)
                .matterType(matterType)
                .usedDelegatedFunctions(hasUsedDelegatedFunctions)
                .applicationContent(objectMapper.convertValue(content, Map.class))
                .caseworker(caseworker)
                .individuals(Collections.EMPTY_SET)
                .proceedings(proceedingSet)
                .decision(decision)
                .isAutoGranted(hasDecision && overallDecision == DecisionStatus.GRANTED));
  }

  private void persistCertificate(ApplicationEntity app) {
    CertificateEntity cert = certificateGenerator.createDefault(b -> b.applicationId(app.getId()));
    persistedDataGenerator.persist(FullCertificateGenerator.class, cert);
  }

  private void flushBatch(List<UUID[]> pendingLinks) {
    persistedDataGenerator.flush();
    linkedIndividualWriter.linkAll(pendingLinks);
    pendingLinks.clear();
    persistedDataGenerator.clear();
  }

  private int linkApplications(Faker faker, List<UUID> persistedAppIds) {
    int linkedCount = 0;
    int i = 0;
    while (i < persistedAppIds.size()) {
      if (faker.number().randomDouble(2, 0, 1) < LINK_RATE && i + 1 < persistedAppIds.size()) {
        UUID leadId = persistedAppIds.get(i);
        i++;
        int associateCount = faker.number().numberBetween(1, 4);
        for (int j = 0; j < associateCount && i < persistedAppIds.size(); j++) {
          persistedDataGenerator.linkApplications(leadId, persistedAppIds.get(i));
          i++;
          linkedCount++;
        }
      } else {
        i++;
      }
    }
    return linkedCount;
  }

  private void printReport(int count, int decidedCount, int linkedCount, long startTime) {
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
    System.out.printf("  Linked applications : %d pairs%n", linkedCount);
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
