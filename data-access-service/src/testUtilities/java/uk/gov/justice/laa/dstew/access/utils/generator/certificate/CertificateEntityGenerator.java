package uk.gov.justice.laa.dstew.access.utils.generator.certificate;

import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.testDto.certificate.CertificateContent;

public class CertificateEntityGenerator extends BaseGenerator<CertificateEntity, CertificateEntity.CertificateEntityBuilder> {

    public CertificateEntityGenerator() {
        super(CertificateEntity::toBuilder, CertificateEntity.CertificateEntityBuilder::build);
    }

    @Override
    public CertificateEntity createDefault() {
        CertificateContent content = DataGenerator.createDefault(CertificateContentGenerator.class);

        return CertificateEntity.builder()
                .applicationId(UUID.randomUUID())
                .certificateContent(Map.of(
                        "certificateNumber", content.getCertificateNumber(),
                        "issueDate", content.getIssueDate(),
                        "validUntil", content.getValidUntil()
                ))
                .createdBy("test-user")
                .updatedBy("test-user")
                .build();
    }
}
