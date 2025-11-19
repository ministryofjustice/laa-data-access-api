package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;

import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;

public class ApplicationRepositoryTest extends BaseIntegrationTest {

    @Test
    public void givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual() {

        // given
        ApplicationEntity expected = applicationFactory.create();
        applicationRepository.save(expected);

        // when
        ApplicationEntity actual = applicationRepository.findById(expected.getId()).orElse(null);

        // then
        assertApplicationEqual(expected, actual);
    }
}
