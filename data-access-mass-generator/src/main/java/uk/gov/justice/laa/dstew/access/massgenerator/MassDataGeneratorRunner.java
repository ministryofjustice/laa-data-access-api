package uk.gov.justice.laa.dstew.access.massgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.LinkedIndividualWriter;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullJsonGenerator;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@Component
public class MassDataGeneratorRunner implements CommandLineRunner {

    private static final int DEFAULT_COUNT = 100;
    private static final int BATCH_SIZE = 500;

    @Autowired
    private PersistedDataGenerator persistedDataGenerator;

    @Autowired
    private LinkedIndividualWriter linkedIndividualWriter;

    @Autowired
    private ObjectMapper objectMapper;

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
                persistedDataGenerator.createAndPersistMultipleRandom(IndividualEntityGenerator.class, 1000);
        System.out.printf("Created %d individuals%n", individuals.size());

        System.out.printf("Generating %d application records...%n", count);

        CategoryOfLawTypeConvertor colConvertor = new CategoryOfLawTypeConvertor();
        MatterTypeConvertor mtConvertor = new MatterTypeConvertor();

        for (int i = 0; i < count; i++) {

            // 1. Generate full rich application content
            ApplicationContent content = new FullJsonGenerator().createDefault();

            // 2. Pick a random caseworker and individual from the pre-generated pools
            CaseworkerEntity cw = caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
            IndividualEntity indiv = individuals.get(faker.number().numberBetween(0, individuals.size()));

            // 3. Derive entity-level columns from the content (mirrors ApplicationContentParserService)
            Proceeding lead = content.getProceedings().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getLeadProceeding()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No lead proceeding found in generated content"));

            CategoryOfLaw col = colConvertor.lenientEnumConversion(lead.getCategoryOfLaw());
            MatterType mt = mtConvertor.lenientEnumConversion(lead.getMatterType());
            boolean udf = content.getProceedings().stream()
                    .anyMatch(p -> Boolean.TRUE.equals(p.getUsedDelegatedFunctions()));

            // 4. Persist ApplicationEntity with all required columns populated
            ApplicationEntity app = persistedDataGenerator.createAndPersist(
                    ApplicationEntityGenerator.class,
                    b -> b
                            .applyApplicationId(content.getId())
                            .laaReference(content.getLaaReference())
                            .submittedAt(Instant.parse(content.getSubmittedAt()))
                            .officeCode(content.getOffice() != null ? content.getOffice().getCode() : null)
                            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                            .categoryOfLaw(col)
                            .matterType(mt)
                            .usedDelegatedFunctions(udf)
                            .applicationContent(objectMapper.convertValue(content, Map.class))
                            .caseworker(cw)
                            // individuals intentionally omitted — set via native query below to
                            // avoid CascadeType.PERSIST attempting to re-persist pool entities
            );

            // 4a. Link individual via native INSERT to bypass CascadeType.PERSIST
            linkedIndividualWriter.link(app.getId(), indiv.getId());

            // 5. Accumulate proceedings then persist as a single saveAllAndFlush (Option C)
            List<ProceedingEntity> proceedingBatch = new ArrayList<>();
            for (Proceeding p : content.getProceedings()) {
                proceedingBatch.add(DataGenerator.createDefault(
                        ProceedingsEntityGenerator.class,
                        b -> b
                                .applicationId(app.getId())
                                .applyProceedingId(p.getId())
                                .description(p.getDescription())
                                .isLead(Boolean.TRUE.equals(p.getLeadProceeding()))
                                .createdBy("mass-generator")
                                .updatedBy("mass-generator")
                                .proceedingContent(objectMapper.convertValue(p, Map.class))
                ));
            }
            persistedDataGenerator.persist(ProceedingsEntityGenerator.class, proceedingBatch);

            // 6. Flush + clear Hibernate session every BATCH_SIZE applications (Option B)
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
        System.out.printf("  Records generated : %d%n", count);
        System.out.printf("  Total time        : %d min %02d sec%n", minutes, seconds);
        System.out.printf("  Throughput        : %.1f records/sec%n", recordsPerSecond);
        System.out.println("========================================");
    }

    private int parseCount(String[] args) {
        if (args != null && args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.printf("Invalid count argument '%s', using default %d%n", args[0], DEFAULT_COUNT);
            }
        }
        return DEFAULT_COUNT;
    }
}

