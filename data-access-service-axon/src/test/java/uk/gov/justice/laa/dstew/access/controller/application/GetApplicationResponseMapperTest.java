package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.InvolvedChild;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.applicationcontent.ScopeLimitation;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.model.DecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.model.PriorAuthoritySummary;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.LinkedApplicationMemberDetails;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

class GetApplicationResponseMapperTest {

  private final GetApplicationResponseMapper mapper = new GetApplicationResponseMapper();

  @Test
  void givenActiveDeletedAndLegacyDocuments_whenMapped_thenReturnsOrderedActiveDocuments() {
    UUID activeId = UUID.randomUUID();
    UUID legacyId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T10:00:00Z");
    ApplicationReadModel application =
        baseReadModel()
            .uploadedDocuments(
                List.of(
                    new UploadDocument(
                        activeId,
                        "GATEWAY_EVIDENCE",
                        uploadedAt.plusSeconds(1),
                        12L,
                        "application/pdf",
                        "checksum",
                        "CIVIL_APPLY",
                        false),
                    new UploadDocument(
                        UUID.randomUUID(),
                        "GATEWAY_EVIDENCE",
                        uploadedAt,
                        13L,
                        "application/pdf",
                        "deleted-checksum",
                        "CIVIL_APPLY",
                        true),
                    new UploadDocument(
                        legacyId,
                        "EXPERT_REPORT",
                        uploadedAt,
                        14L,
                        "application/pdf",
                        "legacy-checksum",
                        "CIVIL_APPLY",
                        false)))
            .documentFilenames(Map.of(activeId, "client-report.pdf"))
            .build();

    var documents = mapper.toResponse(application).getUploadedDocuments();

    assertThat(documents)
        .extracting(document -> document.getDocumentId())
        .containsExactly(legacyId, activeId);
    assertThat(documents.getFirst().getFileName()).isNull();
    assertThat(documents.getLast().getFileName()).isEqualTo("client-report.pdf");
    assertThat(documents.getLast().getDocumentType()).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(documents.getLast().getSize()).isEqualTo(12L);
    assertThat(documents.getLast().getContentType()).isEqualTo("application/pdf");
    assertThat(documents.getLast().getChecksum()).isEqualTo("checksum");
    assertThat(documents.getLast().getSourceService()).isEqualTo("CIVIL_APPLY");
    assertThat(documents.getLast().getUploadedAt())
        .isEqualTo(uploadedAt.plusSeconds(1).atOffset(ZoneOffset.UTC));
  }

  @Test
  void givenNoDocuments_whenMapped_thenReturnsEmptyArray() {
    assertThat(mapper.toResponse(baseReadModel().build()).getUploadedDocuments()).isEmpty();
  }

  private ApplicationReadModel.ApplicationReadModelBuilder baseReadModel() {
    return ApplicationReadModel.builder()
        .applicationId(UUID.randomUUID())
        .status("APPLICATION_SUBMITTED")
        .applicationVersion(1L)
        .applicationDataVersion(1L)
        .createdAt(Instant.parse("2026-01-01T08:00:00Z"))
        .submittedAt(Instant.parse("2026-01-01T09:00:00Z"))
        .modifiedAt(Instant.parse("2026-01-01T10:00:00Z"))
        .client(
            ApplicationClient.builder()
                .firstName("Test")
                .lastName("Client")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .appliedPreviously(false)
                .addresses(List.of(ApplicationAddress.builder().build()))
                .build())
        .autoGranted(AutoGrantedState.MANUAL);
  }

  private Proceeding minimalProceeding(UUID id) {
    return Proceeding.builder()
        .id(id)
        .leadProceeding(true)
        .code("PR001")
        .description("Test proceeding")
        .build();
  }

  @Test
  void givenFullyPopulatedReadModel_whenMapped_thenAllResponseFieldsAreCorrect() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-01-01T09:00:00Z");
    Instant modifiedAt = Instant.parse("2026-01-01T10:00:00Z");

    ApplicationProvider provider =
        ApplicationProvider.builder().officeCode("0B839E").contactEmail("firm@example.com").build();
    Opponent opponent =
        Opponent.builder().opponentType("INDIVIDUAL").firstName("Jane").lastName("Smith").build();
    ScopeLimitation scopeLimitation =
        ScopeLimitation.builder().meaning("SCOPE").description("Full scope").build();
    InvolvedChild child = InvolvedChild.builder().fullName("Child One").build();
    Proceeding proceeding =
        Proceeding.builder()
            .id(proceedingId)
            .leadProceeding(true)
            .code("PR001")
            .description("Test proceeding")
            .categoryOfLaw("FAMILY")
            .matterType("SPECIAL_CHILDREN_ACT")
            .substantiveCostLimitation(BigDecimal.valueOf(1350.00))
            .scopeLimitations(List.of(scopeLimitation))
            .involvedChildren(List.of(child))
            .build();
    ApplicationMeritsDecision meritsDecision = new ApplicationMeritsDecision("REFUSED", null, null);

    ApplicationReadModel readModel =
        baseReadModel()
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .submittedAt(submittedAt)
            .modifiedAt(modifiedAt)
            .decisionStatus("GRANTED")
            .provider(provider)
            .opponents(List.of(opponent))
            .proceedings(List.of(proceeding))
            .meritsDecisions(Map.of(proceedingId, meritsDecision))
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getLastUpdated())
        .isEqualTo(OffsetDateTime.ofInstant(modifiedAt, ZoneOffset.UTC));
    assertThat(response.getSubmittedAt())
        .isEqualTo(OffsetDateTime.ofInstant(submittedAt, ZoneOffset.UTC));
    assertThat(response.getAssignedTo()).isEqualTo(caseworkerId);
    assertThat(response.getDecisionStatus()).isEqualTo(DecisionStatusResponse.GRANTED);
    assertThat(response.getProvider().getOfficeCode()).isEqualTo("0B839E");
    assertThat(response.getProvider().getContactEmail()).isEqualTo("firm@example.com");
    assertThat(response.getOpponents()).hasSize(1);
    assertThat(response.getProceedings()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(response.getProceedings().getFirst().getMatterType())
        .isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(response.getProceedings().getFirst().getSubstantiveCostLimitation())
        .isEqualByComparingTo(BigDecimal.valueOf(1350.00));
    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatusResponse.REFUSED);
    assertThat(response.getProceedings().getFirst().getScopeLimitations()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getInvolvedChildren()).hasSize(1);
  }

  @Test
  void givenLeadWithAssociations_whenMapped_thenMapsResponseFields() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-01-01T08:00:00Z");
    ApplicationReadModel readModel = baseReadModel().applicationId(applicationId).build();
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(applicationId)
            .memberIds(List.of(applicationId, linkedApplicationId))
            .version(4L)
            .build();
    PriorAuthorityReadModel priorAuthority =
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(applicationId)
            .priorAuthorityType("EXPERT")
            .status("DECIDED")
            .decision("GRANTED")
            .createdAt(createdAt)
            .build();

    var response =
        mapper.toResponse(
            readModel,
            group,
            List.of(priorAuthority),
            Map.of(
                linkedApplicationId,
                new LinkedApplicationMemberDetails("LAA-LINKED", "Grace", "Hopper")));

    assertThat(response.getIsLead()).isTrue();
    assertThat(response.getLinkedGroupVersion())
        .isEqualTo(VersionToken.linkedGroup(groupId, 4L).encode());
    assertThat(response.getLinkedApplications())
        .singleElement()
        .satisfies(
            linkedApplication -> {
              assertThat(linkedApplication.getApplicationId()).isEqualTo(linkedApplicationId);
              assertThat(linkedApplication.getLaaReference()).isEqualTo("LAA-LINKED");
              assertThat(linkedApplication.getClientFirstName()).isEqualTo("Grace");
              assertThat(linkedApplication.getClientLastName()).isEqualTo("Hopper");
              assertThat(linkedApplication.getIsLead()).isFalse();
            });
    assertThat(response.getPriorAuthorities())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.getPriorAuthorityId())
                  .isEqualTo(priorAuthority.getPriorAuthorityId());
              assertThat(summary.getStatus()).isEqualTo(PriorAuthoritySummary.StatusEnum.DECIDED);
              assertThat(summary.getPriorAuthorityType())
                  .isEqualTo(PriorAuthoritySummary.PriorAuthorityTypeEnum.EXPERT);
              assertThat(summary.getDecision())
                  .isEqualTo(PriorAuthoritySummary.DecisionEnum.GRANTED);
              assertThat(summary.getCreatedAt())
                  .isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
            });
  }

  @Test
  void givenMemberApplicationWithGroup_whenMapped_thenIncludesLeadAndIsNotLead() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationReadModel readModel =
        baseReadModel().applicationId(applicationId).leadApplicationId(leadApplicationId).build();
    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(leadApplicationId)
            .memberIds(List.of(leadApplicationId, applicationId))
            .build();

    var response =
        mapper.toResponse(
            readModel,
            group,
            List.of(),
            Map.of(
                leadApplicationId,
                new LinkedApplicationMemberDetails("LAA-LEAD", "Ada", "Lovelace")));

    assertThat(response.getIsLead()).isFalse();
    assertThat(response.getLinkedGroupVersion())
        .isEqualTo(VersionToken.linkedGroup(groupId, 0L).encode());
    assertThat(response.getLinkedApplications())
        .singleElement()
        .satisfies(
            linkedApplication -> {
              assertThat(linkedApplication.getApplicationId()).isEqualTo(leadApplicationId);
              assertThat(linkedApplication.getIsLead()).isTrue();
            });
  }

  @Test
  void givenApplicationWithoutRelations_whenMapped_thenReturnsEmptyAssociationLists() {
    var response = mapper.toResponse(baseReadModel().build(), null, List.of(), Map.of());

    assertThat(response.getIsLead()).isFalse();
    assertThat(response.getLinkedGroupVersion()).isNull();
    assertThat(response.getLinkedApplications()).isEmpty();
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenNullSubmittedAt_whenMapped_thenRequiresProjectionTimestamp() {
    ApplicationReadModel readModel =
        baseReadModel().submittedAt(null).decisionStatus("GRANTED").build();

    assertThatThrownBy(() -> mapper.toResponse(readModel))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void givenNullDecisionStatus_whenMapped_thenDecisionStatusIsPending() {
    ApplicationReadModel readModel =
        baseReadModel()
            .submittedAt(Instant.parse("2026-01-01T09:00:00Z"))
            .decisionStatus(null)
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getDecisionStatus()).isEqualTo(DecisionStatusResponse.PENDING);
  }

  @Test
  void givenNullProvider_whenMapped_thenProviderIsNull() {
    ApplicationReadModel readModel = baseReadModel().provider(null).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProvider()).isNull();
  }

  @Test
  void givenProviderWithContactEmailOnly_whenMapped_thenProviderIsReturned() {
    ApplicationProvider provider =
        ApplicationProvider.builder().officeCode(null).contactEmail("firm@example.com").build();
    ApplicationReadModel readModel = baseReadModel().provider(provider).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProvider()).isNotNull();
    assertThat(response.getProvider().getContactEmail()).isEqualTo("firm@example.com");
    assertThat(response.getProvider().getOfficeCode()).isNull();
  }

  @Test
  void givenNullOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationReadModel readModel = baseReadModel().opponents(null).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getOpponents()).isEmpty();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationReadModel readModel = baseReadModel().proceedings(null).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings()).isEmpty();
  }

  @Test
  void givenNullMeritsDecisions_whenMapped_thenMeritsDecisionIsPending() {
    UUID proceedingId = UUID.randomUUID();
    ApplicationReadModel readModel =
        baseReadModel()
            .proceedings(List.of(minimalProceeding(proceedingId)))
            .meritsDecisions(null)
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatusResponse.PENDING);
  }

  @Test
  void givenNullSubstantiveCostLimitation_whenMapped_thenCostLimitationIsNull() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().substantiveCostLimitation(null).build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getSubstantiveCostLimitation()).isNull();
  }

  @Test
  void givenNullDecisionInMeritsDecision_whenMapped_thenMeritsDecisionStatusIsPending() {
    UUID proceedingId = UUID.randomUUID();
    ApplicationMeritsDecision meritsDecision = new ApplicationMeritsDecision(null, null, null);
    ApplicationReadModel readModel =
        baseReadModel()
            .proceedings(List.of(minimalProceeding(proceedingId)))
            .meritsDecisions(Map.of(proceedingId, meritsDecision))
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatusResponse.PENDING);
  }

  @Test
  void givenUnknownCategoryOfLaw_whenMapped_thenCategoryOfLawIsPreserved() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().categoryOfLaw("UNKNOWN_CATEGORY").build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getCategoryOfLaw())
        .isEqualTo("UNKNOWN_CATEGORY");
  }

  @Test
  void givenUnknownMatterType_whenMapped_thenMatterTypeIsPreserved() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().matterType("UNKNOWN_MATTER_TYPE").build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMatterType())
        .isEqualTo("UNKNOWN_MATTER_TYPE");
  }

  @Test
  void givenNullScopeLimitations_whenMapped_thenScopeLimitationsIsEmpty() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().scopeLimitations(null).build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getScopeLimitations()).isEmpty();
  }

  @Test
  void givenNullInvolvedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().involvedChildren(null).build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getInvolvedChildren()).isEmpty();
  }

  @Test
  void givenPopulatedPotentialDuplicates_whenMapped_thenPotentialDuplicatesAreMapped() {
    UUID duplicateId1 = UUID.randomUUID();
    UUID duplicateId2 = UUID.randomUUID();
    List<PotentialDuplicate> potentialDuplicates =
        List.of(
            PotentialDuplicate.builder()
                .applicationId(duplicateId1)
                .laaReference("LAA-00001")
                .legacyReference("LEGACY-001")
                .build(),
            PotentialDuplicate.builder()
                .applicationId(duplicateId2)
                .laaReference("LAA-00002")
                .legacyReference(null)
                .build(),
            PotentialDuplicate.builder()
                .applicationId(null)
                .laaReference("LAA-00003")
                .legacyReference(null)
                .build());
    ApplicationReadModel readModel =
        baseReadModel().potentialDuplicates(potentialDuplicates).build();

    var response = mapper.toResponse(readModel, null, List.of(), Map.of());

    assertThat(response.getPotentialDuplicates()).hasSize(3);
    assertThat(response.getPotentialDuplicates().get(0).getApplicationId()).isEqualTo(duplicateId1);
    assertThat(response.getPotentialDuplicates().get(0).getLaaReference()).isEqualTo("LAA-00001");
    assertThat(response.getPotentialDuplicates().get(0).getLegacyReference())
        .isEqualTo("LEGACY-001");
    assertThat(response.getPotentialDuplicates().get(1).getApplicationId()).isEqualTo(duplicateId2);
    assertThat(response.getPotentialDuplicates().get(1).getLaaReference()).isEqualTo("LAA-00002");
    assertThat(response.getPotentialDuplicates().get(1).getLegacyReference()).isNull();
    assertThat(response.getPotentialDuplicates().get(2).getApplicationId()).isNull();
    assertThat(response.getPotentialDuplicates().get(2).getLaaReference()).isEqualTo("LAA-00003");
    assertThat(response.getPotentialDuplicates().get(2).getLegacyReference()).isNull();
  }

  @Test
  void givenEmptyPotentialDuplicates_whenMapped_thenPotentialDuplicatesIsEmpty() {
    ApplicationReadModel readModel = baseReadModel().potentialDuplicates(List.of()).build();

    var response = mapper.toResponse(readModel, null, List.of(), Map.of());

    assertThat(response.getPotentialDuplicates()).isEmpty();
  }

  @Test
  void givenCreatedApplication_whenMapped_thenReturnsFullCreateContent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-01T09:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-01T10:00:00Z");
    BigDecimal costLimit = new BigDecimal("1350.25");
    ApplicationAddress address =
        ApplicationAddress.builder()
            .addressLineOne("1 Example Street")
            .addressLineTwo("Flat 2")
            .addressLineThree("District")
            .city("London")
            .county("Greater London")
            .postcode("SW1A 1AA")
            .organisation("Example Ltd")
            .buildingNumberName("One")
            .countryCode("GB")
            .countryName("United Kingdom")
            .location("HOME")
            .lookupUsed(true)
            .careOf("PERSON")
            .careOfFirstName("Care")
            .careOfLastName("Of")
            .careOfOrganisationName("Care Org")
            .build();
    ApplicationClient client =
        ApplicationClient.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .lastNameAtBirth("Byron")
            .dateOfBirth(LocalDate.of(1815, 12, 10))
            .hasNationalInsuranceNumber(true)
            .nationalInsuranceNumber("QQ123456C")
            .appliedPreviously(true)
            .previousApplicationId("APP-PREVIOUS")
            .relationshipToInvolvedChildren("PARENT")
            .addresses(List.of(address))
            .build();
    ScopeLimitation scope =
        ScopeLimitation.builder()
            .code("SCOPE-1")
            .type("STANDARD")
            .meaning("Defined scope")
            .description("Scope detail")
            .build();
    InvolvedChild child =
        InvolvedChild.builder()
            .fullName("Child Name")
            .dateOfBirth(LocalDate.of(2015, 4, 3))
            .build();
    Proceeding proceeding =
        Proceeding.builder()
            .id(proceedingId)
            .leadProceeding(true)
            .code("PR-123")
            .meaning("Proceeding meaning")
            .description("Proceeding description")
            .matterType("Unknown matter")
            .matterTypeCode("MAT-123")
            .categoryOfLaw("Unknown law")
            .categoryOfLawCode("CAT-123")
            .clientInvolvementType("Applicant")
            .clientInvolvementTypeCode("CLIENT-1")
            .usedDelegatedFunctions(true)
            .delegatedFunctionsDate(LocalDate.of(2026, 7, 20))
            .delegatedFunctionsCostLimitation(new BigDecimal("2250.50"))
            .substantiveLevelOfService(1)
            .substantiveLevelOfServiceName("Full representation")
            .emergencyLevelOfService(2)
            .emergencyLevelOfServiceName("Emergency representation")
            .substantiveCostLimitation(costLimit)
            .scopeLimitations(List.of(scope))
            .involvedChildren(List.of(child))
            .build();
    ApplicationReadModel application =
        baseReadModel()
            .applicationId(applicationId)
            .createdAt(createdAt)
            .submittedAt(submittedAt)
            .modifiedAt(submittedAt)
            .laaReference("LAA-123")
            .client(client)
            .officeCode("OFFICE-1")
            .provider(
                ApplicationProvider.builder()
                    .officeCode("OFFICE-1")
                    .contactEmail("provider@example.com")
                    .build())
            .opponents(
                List.of(
                    Opponent.builder()
                        .opponentType("INDIVIDUAL")
                        .firstName("Pat")
                        .lastName("Opponent")
                        .organisationName("Opponent Org")
                        .build()))
            .proceedings(List.of(proceeding))
            .meritsDecisions(Map.of())
            .usedDelegatedFunctions(true)
            .build();

    var response = mapper.toResponse(application);

    assertThat(response.getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getStatus().getValue()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(response.getLaaReference()).isEqualTo("LAA-123");
    assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getSubmittedAt()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getLastUpdated()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getIsLead()).isFalse();
    assertThat(response.getUsedDelegatedFunctions()).isTrue();
    assertThat(response.getClient().getFirstName()).isEqualTo("Ada");
    assertThat(response.getClient().getLastName()).isEqualTo("Lovelace");
    assertThat(response.getClient().getLastNameAtBirth()).isEqualTo("Byron");
    assertThat(response.getClient().getDateOfBirth()).isEqualTo(client.getDateOfBirth());
    assertThat(response.getClient().getHasNationalInsuranceNumber()).isTrue();
    assertThat(response.getClient().getNationalInsuranceNumber()).isEqualTo("QQ123456C");
    assertThat(response.getClient().getAppliedPreviously()).isTrue();
    assertThat(response.getClient().getPreviousApplicationId()).isEqualTo("APP-PREVIOUS");
    assertThat(response.getClient().getRelationshipToInvolvedChildren()).isEqualTo("PARENT");
    assertThat(response.getClient().getAddresses()).hasSize(1);
    var addressResponse = response.getClient().getAddresses().getFirst();
    assertThat(addressResponse.getAddressLineOne()).isEqualTo("1 Example Street");
    assertThat(addressResponse.getAddressLineTwo()).isEqualTo("Flat 2");
    assertThat(addressResponse.getAddressLineThree()).isEqualTo("District");
    assertThat(addressResponse.getCity()).isEqualTo("London");
    assertThat(addressResponse.getCounty()).isEqualTo("Greater London");
    assertThat(addressResponse.getPostcode()).isEqualTo("SW1A 1AA");
    assertThat(addressResponse.getOrganisation()).isEqualTo("Example Ltd");
    assertThat(addressResponse.getBuildingNumberName()).isEqualTo("One");
    assertThat(addressResponse.getCountryCode()).isEqualTo("GB");
    assertThat(addressResponse.getCountryName()).isEqualTo("United Kingdom");
    assertThat(addressResponse.getLocation()).isEqualTo("HOME");
    assertThat(addressResponse.getLookupUsed()).isTrue();
    assertThat(addressResponse.getCareOf()).isEqualTo("PERSON");
    assertThat(addressResponse.getCareOfFirstName()).isEqualTo("Care");
    assertThat(addressResponse.getCareOfLastName()).isEqualTo("Of");
    assertThat(addressResponse.getCareOfOrganisationName()).isEqualTo("Care Org");
    assertThat(response.getProvider().getOfficeCode()).isEqualTo("OFFICE-1");
    assertThat(response.getProvider().getContactEmail()).isEqualTo("provider@example.com");
    assertThat(response.getOpponents())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getOpponentType()).isEqualTo("INDIVIDUAL");
              assertThat(mapped.getFirstName()).isEqualTo("Pat");
              assertThat(mapped.getLastName()).isEqualTo("Opponent");
              assertThat(mapped.getOrganisationName()).isEqualTo("Opponent Org");
            });
    var mappedProceeding = response.getProceedings().getFirst();
    assertThat(mappedProceeding.getProceedingId()).isEqualTo(proceedingId);
    assertThat(mappedProceeding.getLeadProceeding()).isTrue();
    assertThat(mappedProceeding.getCode()).isEqualTo("PR-123");
    assertThat(mappedProceeding.getMeaning()).isEqualTo("Proceeding meaning");
    assertThat(mappedProceeding.getDescription()).isEqualTo("Proceeding description");
    assertThat(mappedProceeding.getMatterType()).isEqualTo("Unknown matter");
    assertThat(mappedProceeding.getMatterTypeCode()).isEqualTo("MAT-123");
    assertThat(mappedProceeding.getCategoryOfLaw()).isEqualTo("Unknown law");
    assertThat(mappedProceeding.getCategoryOfLawCode()).isEqualTo("CAT-123");
    assertThat(mappedProceeding.getClientInvolvementType()).isEqualTo("Applicant");
    assertThat(mappedProceeding.getClientInvolvementTypeCode()).isEqualTo("CLIENT-1");
    assertThat(mappedProceeding.getUsedDelegatedFunctions()).isTrue();
    assertThat(mappedProceeding.getDelegatedFunctionsDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(mappedProceeding.getDelegatedFunctionsCostLimitation())
        .isEqualByComparingTo("2250.50");
    assertThat(mappedProceeding.getSubstantiveLevelOfServiceCode()).isEqualTo(1);
    assertThat(mappedProceeding.getSubstantiveLevelOfServiceName())
        .isEqualTo("Full representation");
    assertThat(mappedProceeding.getEmergencyLevelOfServiceCode()).isEqualTo(2);
    assertThat(mappedProceeding.getEmergencyLevelOfServiceName())
        .isEqualTo("Emergency representation");
    assertThat(mappedProceeding.getSubstantiveCostLimitation()).isEqualByComparingTo(costLimit);
    assertThat(mappedProceeding.getScopeLimitations())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getCode()).isEqualTo("SCOPE-1");
              assertThat(mapped.getType()).isEqualTo("STANDARD");
              assertThat(mapped.getMeaning()).isEqualTo("Defined scope");
              assertThat(mapped.getDescription()).isEqualTo("Scope detail");
            });
    assertThat(mappedProceeding.getInvolvedChildren())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getFirstName()).isNull();
              assertThat(mapped.getLastName()).isNull();
              assertThat(mapped.getDateOfBirth()).isEqualTo(LocalDate.of(2015, 4, 3));
            });
    assertThat(response.getAutoGranted().getValue()).isEqualTo("MANUAL");
    assertThat(response.getPotentialDuplicates()).isNull();
    assertThat(response.getVersion()).isEqualTo(1L);
  }

  @Test
  void givenNoDecisions_whenMapped_thenReturnsPending() {
    Proceeding proceeding = minimalProceeding(UUID.randomUUID());
    ApplicationReadModel application =
        baseReadModel()
            .createdAt(Instant.parse("2026-08-01T09:00:00Z"))
            .submittedAt(Instant.parse("2026-08-01T10:00:00Z"))
            .proceedings(List.of(proceeding))
            .meritsDecisions(Map.of())
            .decisionStatus(null)
            .build();

    var response = mapper.toResponse(application);

    assertThat(response.getDecisionStatus()).isEqualTo(DecisionStatusResponse.PENDING);
    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatusResponse.PENDING);
  }

  @Test
  void givenUnknownLegalCategories_whenMapped_thenPreservesStringsAndCodes() {
    Proceeding proceeding =
        minimalProceeding(UUID.randomUUID()).toBuilder()
            .categoryOfLaw("A category not in the old enum")
            .categoryOfLawCode("CAT-991")
            .matterType("A matter not in the old enum")
            .matterTypeCode("MAT-123")
            .build();
    ApplicationReadModel application =
        baseReadModel()
            .createdAt(Instant.parse("2026-08-01T09:00:00Z"))
            .submittedAt(Instant.parse("2026-08-01T10:00:00Z"))
            .proceedings(List.of(proceeding))
            .meritsDecisions(Map.of())
            .build();

    var mapped = mapper.toResponse(application).getProceedings().getFirst();

    assertThat(mapped.getCategoryOfLaw()).isEqualTo("A category not in the old enum");
    assertThat(mapped.getCategoryOfLawCode()).isEqualTo("CAT-991");
    assertThat(mapped.getMatterType()).isEqualTo("A matter not in the old enum");
    assertThat(mapped.getMatterTypeCode()).isEqualTo("MAT-123");
  }

  @Test
  void givenLinkedMembers_whenMapped_thenIncludesClientNames() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedId = UUID.randomUUID();
    var group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(UUID.randomUUID())
            .leadApplicationId(applicationId)
            .memberIds(List.of(applicationId, linkedId))
            .build();
    ApplicationReadModel application =
        baseReadModel()
            .applicationId(applicationId)
            .createdAt(Instant.parse("2026-08-01T09:00:00Z"))
            .submittedAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build();

    var response =
        mapper.toResponse(
            application,
            group,
            List.of(),
            Map.of(linkedId, new LinkedApplicationMemberDetails("LAA-LINKED", "Grace", "Hopper")));

    assertThat(response.getLinkedApplications())
        .singleElement()
        .satisfies(
            member -> {
              assertThat(member.getApplicationId()).isEqualTo(linkedId);
              assertThat(member.getLaaReference()).isEqualTo("LAA-LINKED");
              assertThat(member.getClientFirstName()).isEqualTo("Grace");
              assertThat(member.getClientLastName()).isEqualTo("Hopper");
              assertThat(member.getIsLead()).isFalse();
            });
  }

  @Test
  void givenNullPotentialDuplicates_whenMapped_thenPotentialDuplicatesIsNull() {
    ApplicationReadModel readModel = baseReadModel().potentialDuplicates(null).build();

    var response = mapper.toResponse(readModel, null, List.of(), Map.of());

    assertThat(response.getPotentialDuplicates()).isNull();
  }
}
