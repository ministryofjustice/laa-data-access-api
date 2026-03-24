package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.util.Map;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullCertificateGenerator
        extends BaseGenerator<CertificateEntity, CertificateEntity.CertificateEntityBuilder> {

    public FullCertificateGenerator() {
        super(CertificateEntity::toBuilder, CertificateEntity.CertificateEntityBuilder::build);
    }

    @Override
    public CertificateEntity createDefault() {
        return CertificateEntity.builder()
                .certificateContent(Map.of(
                        "certificateNumber", faker.regexify("[0-9]{4}/[0-9]{3} LEGAL HELP [A-Z]{3}"),
                        "issueDate", getRandomDate().toString(),
                        "validUntil", getRandomDate().plusYears(1).toString()
                ))
                .createdBy("mass-generator")
                .updatedBy("mass-generator")
                .build();
    }
}

