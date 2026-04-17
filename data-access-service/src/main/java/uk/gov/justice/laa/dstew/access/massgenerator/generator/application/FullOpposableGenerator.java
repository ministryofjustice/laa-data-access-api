package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullOpposable;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullOpposableGenerator extends BaseGenerator<FullOpposable, FullOpposable.FullOpposableBuilder> {

    public FullOpposableGenerator() {
        super(FullOpposable::toBuilder, FullOpposable.FullOpposableBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public FullOpposable createDefault() {
        return FullOpposable.builder()
                .id(UUID.randomUUID().toString())
                .createdAt(randomInstant())
                .updatedAt(randomInstant())
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .build();
    }
}

