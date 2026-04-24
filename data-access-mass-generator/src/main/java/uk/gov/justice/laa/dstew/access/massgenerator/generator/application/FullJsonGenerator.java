package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullJsonGenerator
    extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {

  private final FullOfficeGenerator officeGenerator = new FullOfficeGenerator();
  private final FullProviderGenerator providerGenerator = new FullProviderGenerator();
  private final FullApplicantGenerator applicantGenerator = new FullApplicantGenerator();
  private final FullBenefitCheckResultGenerator benefitCheckResultGenerator =
      new FullBenefitCheckResultGenerator();
  private final FullLegalFrameworkMeritsTaskListGenerator legalFrameworkMeritsTaskListGenerator =
      new FullLegalFrameworkMeritsTaskListGenerator();
  private final FullStateMachineGenerator stateMachineGenerator = new FullStateMachineGenerator();
  private final FullMeansGenerator meansGenerator = new FullMeansGenerator();
  private final FullApplicationMeritsGenerator applicationMeritsGenerator =
      new FullApplicationMeritsGenerator();
  private final FullProceedingGenerator proceedingGenerator = new FullProceedingGenerator();
  private final FullProceedingMeritsGenerator proceedingMeritsGenerator =
      new FullProceedingMeritsGenerator();

  public FullJsonGenerator() {
    super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public ApplicationContent createDefault() {
    return ApplicationContent.builder()
        .id(UUID.randomUUID())
        .submittedAt(randomInstant())
        .status(faker.options().option("generating_reports", "submitted", "draft"))
        .laaReference(faker.regexify("L-[A-Z]{3}-[A-Z][0-9]{2}-[0-9]"))
        .lastNameAtBirth(faker.name().lastName())
        //                .previousApplicationReference(faker.regexify("[A-Z]{2}[0-9]{3}[A-Z]"))
        //                .relationshipToChildren(faker.options().option("father", "mother",
        // "guardian"))
        .correspondenceAddressType(faker.options().option("Home", "office"))
        .office(officeGenerator.createDefault())
        .applicationMerits(applicationMeritsGenerator.createDefault())
        .proceedings(
            List.of(
                proceedingGenerator.createDefault(b -> b.leadProceeding(true)),
                proceedingGenerator.createDefault(b -> b.leadProceeding(false)),
                proceedingGenerator.createDefault(b -> b.leadProceeding(false))))
        .submitterEmail(faker.internet().emailAddress())
        .build()
        .putAdditionalApplicationContent(
            "applicationRef", faker.regexify("L-[A-Z]{3}-[A-Z][0-9]{2}-[0-9]"))
        .putAdditionalApplicationContent("createdAt", randomInstant())
        .putAdditionalApplicationContent("updatedAt", randomInstant())
        .putAdditionalApplicationContent("applicantId", UUID.randomUUID().toString())
        .putAdditionalApplicationContent("hasOfflineAccounts", null)
        .putAdditionalApplicationContent("openBankingConsent", null)
        .putAdditionalApplicationContent("openBankingConsentChoiceAt", null)
        .putAdditionalApplicationContent("ownHome", null)
        .putAdditionalApplicationContent("propertyValue", null)
        .putAdditionalApplicationContent("sharedOwnership", null)
        .putAdditionalApplicationContent("outstandingMortgageAmount", null)
        .putAdditionalApplicationContent("percentageHome", null)
        .putAdditionalApplicationContent(
            "providerStep", faker.options().option("submitted_applications", "provider_details"))
        .putAdditionalApplicationContent("providerId", UUID.randomUUID().toString())
        .putAdditionalApplicationContent("draft", false)
        .putAdditionalApplicationContent("transactionPeriodStartOn", null)
        .putAdditionalApplicationContent("transactionPeriodFinishOn", null)
        .putAdditionalApplicationContent("transactionsGathered", null)
        .putAdditionalApplicationContent("completedAt", null)
        .putAdditionalApplicationContent("declarationAcceptedAt", null)
        .putAdditionalApplicationContent("providerStepParams", Map.of())
        .putAdditionalApplicationContent("ownVehicle", null)
        .putAdditionalApplicationContent("substantiveApplicationDeadlineOn", null)
        .putAdditionalApplicationContent("substantiveApplication", null)
        .putAdditionalApplicationContent("hasDependants", null)
        .putAdditionalApplicationContent("officeId", UUID.randomUUID().toString())
        .putAdditionalApplicationContent("hasRestrictions", null)
        .putAdditionalApplicationContent("restrictionsDetails", null)
        .putAdditionalApplicationContent("noCreditTransactionTypesSelected", null)
        .putAdditionalApplicationContent("noDebitTransactionTypesSelected", null)
        .putAdditionalApplicationContent("providerReceivedCitizenConsent", null)
        .putAdditionalApplicationContent("discardedAt", null)
        .putAdditionalApplicationContent("inScopeOfLaspo", null)
        .putAdditionalApplicationContent("emergencyCostOverride", null)
        .putAdditionalApplicationContent("emergencyCostRequested", null)
        .putAdditionalApplicationContent("emergencyCostReasons", null)
        .putAdditionalApplicationContent("noCashIncome", null)
        .putAdditionalApplicationContent("noCashOutgoings", null)
        .putAdditionalApplicationContent("purgeableOn", null)
        .putAdditionalApplicationContent("allowedDocumentCategories", List.of())
        .putAdditionalApplicationContent("extraEmploymentInformation", null)
        .putAdditionalApplicationContent("extraEmploymentInformationDetails", null)
        .putAdditionalApplicationContent("fullEmploymentDetails", null)
        .putAdditionalApplicationContent("clientDeclarationConfirmedAt", randomInstant())
        .putAdditionalApplicationContent("substantiveCostOverride", null)
        .putAdditionalApplicationContent("substantiveCostRequested", null)
        .putAdditionalApplicationContent("substantiveCostReasons", null)
        .putAdditionalApplicationContent("applicantInReceiptOfHousingBenefit", null)
        .putAdditionalApplicationContent("copyCase", faker.bool().bool())
        .putAdditionalApplicationContent("copyCaseId", UUID.randomUUID().toString())
        .putAdditionalApplicationContent("caseCloned", faker.bool().bool())
        .putAdditionalApplicationContent("separateRepresentationRequired", faker.bool().bool())
        .putAdditionalApplicationContent("plfCourtOrder", null)
        .putAdditionalApplicationContent(
            "reviewed",
            Map.of("checkProviderAnswers", Map.of("status", "completed", "at", randomInstant())))
        .putAdditionalApplicationContent("dwpResultConfirmed", faker.bool().bool())
        .putAdditionalApplicationContent("linkedApplicationCompleted", faker.bool().bool())
        .putAdditionalApplicationContent("autoGrant", false)
        .putAdditionalApplicationContent("provider", providerGenerator.createDefault())
        .putAdditionalApplicationContent("applicant", applicantGenerator.createDefault())
        .putAdditionalApplicationContent("partner", null)
        .putAdditionalApplicationContent("allLinkedApplications", List.of())
        .putAdditionalApplicationContent(
            "benefitCheckResult", benefitCheckResultGenerator.createDefault())
        .putAdditionalApplicationContent("dwpOverride", null)
        .putAdditionalApplicationContent(
            "legalFrameworkMeritsTaskList", legalFrameworkMeritsTaskListGenerator.createDefault())
        .putAdditionalApplicationContent("stateMachine", stateMachineGenerator.createDefault())
        .putAdditionalApplicationContent("hmrcResponses", List.of())
        .putAdditionalApplicationContent("employments", List.of())
        .putAdditionalApplicationContent("means", meansGenerator.createDefault())
        .putAdditionalApplicationContent(
            "proceedingMerits",
            List.of(
                proceedingMeritsGenerator.createDefault(),
                proceedingMeritsGenerator.createDefault(),
                proceedingMeritsGenerator.createDefault()));
  }
}
