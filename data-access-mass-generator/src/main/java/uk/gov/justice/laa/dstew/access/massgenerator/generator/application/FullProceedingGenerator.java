package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullProceedingGenerator extends BaseGenerator<Proceeding, Proceeding.ProceedingBuilder> {

    private final FullScopeLimitationGenerator scopeLimitationGenerator = new FullScopeLimitationGenerator();

    public FullProceedingGenerator() {
        super(Proceeding::toBuilder, Proceeding.ProceedingBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public Proceeding createDefault() {
        return Proceeding.builder()
                .id(UUID.randomUUID())
                .categoryOfLaw("FAMILY")
                .matterType("SPECIAL_CHILDREN_ACT")
                .leadProceeding(faker.bool().bool())
                .usedDelegatedFunctions(faker.bool().bool())
                .description(faker.lorem().sentence())
                .meaning(faker.options().option("Care order", "Parental responsibility", "Emergency protection order - discharge"))
                .usedDelegatedFunctionsOn(getRandomDate())
                .substantiveCostLimitation(faker.number().numberBetween(1000, 50000) + ".0")
                .substantiveLevelOfServiceName(faker.options().option("Full Representation", "Limited Case Work"))
                .build()
                .putAdditionalProperty("legalAidApplicationId", UUID.randomUUID().toString())
                .putAdditionalProperty("proceedingCaseId", faker.number().numberBetween(50000000, 59999999))
                .putAdditionalProperty("delegatedFunctionsCostLimitation", "2250.0")
                .putAdditionalProperty("usedDelegatedFunctionsReportedOn", getRandomDate().toString())
                .putAdditionalProperty("createdAt", randomInstant())
                .putAdditionalProperty("updatedAt", randomInstant())
                .putAdditionalProperty("name", faker.options().option("app_for_care_order_sca", "parental_responsibility_sca", "app_discharge_emergency_sca"))
                .putAdditionalProperty("categoryLawCode", faker.options().option("MAT", "CRM"))
                .putAdditionalProperty("ccmsCode", faker.regexify("PB[0-9]{3}"))
                .putAdditionalProperty("ccmsMatterCode", faker.regexify("[A-Z]{5}"))
                .putAdditionalProperty("clientInvolvementTypeCCMSCode", faker.options().option("W", "D", "A"))
                .putAdditionalProperty("clientInvolvementTypeDescription", faker.options().option("A child subject of the proceeding", "Defendant", "Applicant"))
                .putAdditionalProperty("emergencyLevelOfService", null)
                .putAdditionalProperty("emergencyLevelOfServiceName", null)
                .putAdditionalProperty("emergencyLevelOfServiceStage", null)
                .putAdditionalProperty("substantiveLevelOfService", faker.number().numberBetween(1, 5))
                .putAdditionalProperty("substantiveLevelOfServiceStage", faker.number().numberBetween(1, 10))
                .putAdditionalProperty("acceptedEmergencyDefaults", null)
                .putAdditionalProperty("acceptedSubstantiveDefaults", faker.bool().bool())
                .putAdditionalProperty("scaType", faker.options().option("core", "related"))
                .putAdditionalProperty("relatedOrders", List.of())
                .putAdditionalProperty("finalHearings", List.of())
                .putAdditionalProperty("scopeLimitations", List.of(scopeLimitationGenerator.createDefault()));
    }
}

