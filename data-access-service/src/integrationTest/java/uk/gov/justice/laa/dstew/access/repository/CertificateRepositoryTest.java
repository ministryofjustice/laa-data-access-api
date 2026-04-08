package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class CertificateRepositoryTest extends BaseIntegrationTest {

    @Test
    public void givenSaveOfExpectedCertificate_whenGetCalled_expectedAndActualAreEqual() {

        // given
        ApplicationEntity applicationEntity = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        CertificateEntity expected = persistedDataGenerator.createAndPersist(CertificateEntityGenerator.class,
                builder -> builder.applicationId(applicationEntity.getId()));
        clearCache();

        // when
        CertificateEntity actual = certificateRepository.findById(expected.getId()).orElse(null);

        // then
        assertCertificateEqual(expected, actual);
    }

    private void assertCertificateEqual(CertificateEntity expected, CertificateEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "modifiedAt")
                .isEqualTo(actual);
        assertThat(expected.getCreatedAt()).isNotNull();
        assertThat(expected.getModifiedAt()).isNotNull();
    }
}
