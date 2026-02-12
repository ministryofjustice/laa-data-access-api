package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ApplicationRepositoryTest extends BaseIntegrationTest {

    @Test
    public void givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual() {

        // given
        ApplicationEntity expected = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));
        ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class, builder -> {
                builder.applicationId(expected.getId());
        });
        DecisionEntity expectedDecision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, builder -> {
            builder.meritsDecisions(Set.of(DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class, mBuilder -> {
                mBuilder.proceeding(proceeding);
            })));
        });
        expected.setDecision(expectedDecision);
        applicationRepository.saveAndFlush(expected);
        clearCache();

        // when
        ApplicationEntity actual = applicationRepository.findById(expected.getId()).orElse(null);

        // then
        assertApplicationEqual(expected, actual);
    }

    private void assertApplicationEqual(ApplicationEntity expected, ApplicationEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "modifiedAt", "individuals")
                .isEqualTo(actual);
        assertThat(expected.getCreatedAt()).isNotNull();
        assertThat(expected.getModifiedAt()).isNotNull();
    }
}
