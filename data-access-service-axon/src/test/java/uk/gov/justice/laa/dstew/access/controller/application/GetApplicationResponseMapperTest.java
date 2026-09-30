package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.InvolvedChild;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.applicationcontent.ScopeLimitation;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;

class GetApplicationResponseMapperTest {

  private final GetApplicationResponseMapper mapper = new GetApplicationResponseMapper();

  private ApplicationReadModel.ApplicationReadModelBuilder baseReadModel() {
    return ApplicationReadModel.builder()
        .applicationId(UUID.randomUUID())
        .status("APPLICATION_SUBMITTED")
        .applicationVersion(1L)
        .applicationDataVersion(1L)
        .modifiedAt(Instant.parse("2026-01-01T10:00:00Z"))
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
    assertThat(response.getDecisionStatus()).isEqualTo(DecisionStatus.GRANTED);
    assertThat(response.getProvider().getOfficeCode()).isEqualTo("0B839E");
    assertThat(response.getProvider().getContactEmail()).isEqualTo("firm@example.com");
    assertThat(response.getOpponents()).hasSize(1);
    assertThat(response.getProceedings()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getCategoryOfLaw())
        .isEqualTo(CategoryOfLaw.FAMILY);
    assertThat(response.getProceedings().getFirst().getMatterType())
        .isEqualTo(MatterType.SPECIAL_CHILDREN_ACT);
    assertThat(response.getProceedings().getFirst().getSubstantiveCostLimitation())
        .isEqualTo(1350.0);
    assertThat(response.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatus.REFUSED);
    assertThat(response.getProceedings().getFirst().getScopeLimitations()).hasSize(1);
    assertThat(response.getProceedings().getFirst().getInvolvedChildren()).hasSize(1);
  }

  @Test
  void givenNullSubmittedAt_whenMapped_thenSubmittedAtIsNull() {
    ApplicationReadModel readModel =
        baseReadModel().submittedAt(null).decisionStatus("GRANTED").build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getSubmittedAt()).isNull();
  }

  @Test
  void givenNullDecisionStatus_whenMapped_thenDecisionStatusIsNull() {
    ApplicationReadModel readModel =
        baseReadModel()
            .submittedAt(Instant.parse("2026-01-01T09:00:00Z"))
            .decisionStatus(null)
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getDecisionStatus()).isNull();
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
  void givenNullMeritsDecisions_whenMapped_thenMeritsDecisionIsNull() {
    UUID proceedingId = UUID.randomUUID();
    ApplicationReadModel readModel =
        baseReadModel()
            .proceedings(List.of(minimalProceeding(proceedingId)))
            .meritsDecisions(null)
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMeritsDecision()).isNull();
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
  void givenNullDecisionInMeritsDecision_whenMapped_thenMeritsDecisionStatusIsNull() {
    UUID proceedingId = UUID.randomUUID();
    ApplicationMeritsDecision meritsDecision = new ApplicationMeritsDecision(null, null, null);
    ApplicationReadModel readModel =
        baseReadModel()
            .proceedings(List.of(minimalProceeding(proceedingId)))
            .meritsDecisions(Map.of(proceedingId, meritsDecision))
            .build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMeritsDecision()).isNull();
  }

  @Test
  void givenUnknownCategoryOfLaw_whenMapped_thenCategoryOfLawIsNull() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().categoryOfLaw("UNKNOWN_CATEGORY").build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getCategoryOfLaw()).isNull();
  }

  @Test
  void givenUnknownMatterType_whenMapped_thenMatterTypeIsNull() {
    UUID proceedingId = UUID.randomUUID();
    Proceeding proceeding =
        minimalProceeding(proceedingId).toBuilder().matterType("UNKNOWN_MATTER_TYPE").build();
    ApplicationReadModel readModel =
        baseReadModel().proceedings(List.of(proceeding)).meritsDecisions(Map.of()).build();

    var response = mapper.toResponse(readModel);

    assertThat(response.getProceedings().getFirst().getMatterType()).isNull();
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
}
