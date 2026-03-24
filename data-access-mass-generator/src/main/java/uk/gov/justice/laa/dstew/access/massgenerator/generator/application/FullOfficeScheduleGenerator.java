package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullOfficeSchedule;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullOfficeScheduleGenerator extends BaseGenerator<FullOfficeSchedule, FullOfficeSchedule.FullOfficeScheduleBuilder> {

    public FullOfficeScheduleGenerator() {
        super(FullOfficeSchedule::toBuilder, FullOfficeSchedule.FullOfficeScheduleBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public FullOfficeSchedule createDefault() {
        return FullOfficeSchedule.builder()
                .id(UUID.randomUUID().toString())
                .officeId(UUID.randomUUID().toString())
                .areaOfLaw(faker.options().option("LEGAL HELP", "FAMILY MEDIATION", "CRIME"))
                .categoryOfLaw(faker.options().option("MAT", "CRM", "HOU"))
                .authorisationStatus(faker.options().option("APPROVED", "PENDING", "SUSPENDED"))
                .status(faker.options().option("Open", "Closed"))
                .startDate(getRandomDate().toString())
                .endDate(LocalDate.of(2099, 12, 31).toString())
                .cancelled(faker.bool().bool())
                .licenseIndicator(faker.number().numberBetween(1, 5))
                .devolvedPowerStatus(faker.options().option("Yes - Excluding JR Proceedings", "No", "Yes"))
                .createdAt(randomInstant())
                .updatedAt(randomInstant())
                .build();
    }
}

