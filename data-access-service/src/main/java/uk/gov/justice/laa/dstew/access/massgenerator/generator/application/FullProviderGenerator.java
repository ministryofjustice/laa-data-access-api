package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullProvider;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullProviderGenerator extends BaseGenerator<FullProvider, FullProvider.FullProviderBuilder> {

    public FullProviderGenerator() {
        super(FullProvider::toBuilder, FullProvider.FullProviderBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public FullProvider createDefault() {
        return FullProvider.builder()
                .id(UUID.randomUUID().toString())
                .username(faker.regexify("[A-Z]{5}-[A-Z]{5}-[A-Z]{3}-LLP[0-9]"))
                .type(null)
                .roles(null)
                .createdAt(randomInstant())
                .updatedAt(randomInstant())
                .officeCodes(faker.regexify("[0-9][A-Z][0-9]{3}[A-Z]:[0-9][A-Z][0-9]{3}[A-Z]"))
                .firmId(UUID.randomUUID().toString())
                .selectedOfficeId(UUID.randomUUID().toString())
                .name(faker.name().fullName())
                .email(faker.internet().emailAddress())
                .ccmsContactId(faker.number().numberBetween(10000000L, 99999999L))
                .silasId(UUID.randomUUID().toString())
                .build();
    }
}

