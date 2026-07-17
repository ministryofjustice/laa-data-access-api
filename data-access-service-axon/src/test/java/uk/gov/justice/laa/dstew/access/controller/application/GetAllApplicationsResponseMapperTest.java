package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;

class GetAllApplicationsResponseMapperTest {

  private GetAllApplicationsResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllApplicationsResponseMapper();
  }

  @Test
  void givenEmptyResult_whenToResponse_thenReturnsEmptyListWith200() {
    FindAllApplicationsResult result =
        new FindAllApplicationsResult(List.of(), Map.of(), 0L, 1, 20);

    ResponseEntity<ApplicationSummaryResponse> response = mapper.toResponse(result);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getApplications()).isEmpty();
    assertThat(response.getBody().getPaging().getTotalRecords()).isZero();
    assertThat(response.getBody().getPaging().getPage()).isEqualTo(1);
    assertThat(response.getBody().getPaging().getPageSize()).isEqualTo(20);
    assertThat(response.getBody().getPaging().getItemsReturned()).isZero();
  }

  @Test
  void givenApplication_whenToResponse_thenMapsColumnarFieldsCorrectly() {
    UUID applicationId = UUID.randomUUID();
    ApplicationReadModel app =
        ApplicationReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .laaReference("LAA-999")
            .officeCode("2B002C")
            .matterType("SPECIAL_CHILDREN_ACT")
            .categoryOfLaw("FAMILY")
            .usedDelegatedFunctions(true)
            .submittedAt(Instant.parse("2026-07-01T10:00:00Z"))
            .modifiedAt(Instant.parse("2026-07-02T10:00:00Z"))
            .individuals(List.of())
            .build();

    FindAllApplicationsResult result =
        new FindAllApplicationsResult(List.of(app), Map.of(), 1L, 1, 20);

    ApplicationSummary summary = mapper.toResponse(result).getBody().getApplications().get(0);

    assertThat(summary.getApplicationId()).isEqualTo(applicationId);
    assertThat(summary.getStatus().name()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(summary.getLaaReference()).isEqualTo("LAA-999");
    assertThat(summary.getOfficeCode()).isEqualTo("2B002C");
    assertThat(summary.getUsedDelegatedFunctions()).isTrue();
    assertThat(summary.getSubmittedAt()).isNotNull();
    assertThat(summary.getLastUpdated()).isNotNull();
  }

  @Test
  void givenApplicationWithNoLeadId_whenToResponse_thenIsLeadTrue() {
    ApplicationReadModel app =
        ApplicationReadModel.builder()
            .applicationId(UUID.randomUUID())
            .modifiedAt(Instant.now())
            .leadApplicationId(null)
            .individuals(List.of())
            .build();

    ApplicationSummary summary =
        mapper.toResponse(new FindAllApplicationsResult(List.of(app), Map.of(), 1L, 1, 20))
            .getBody()
            .getApplications()
            .get(0);

    assertThat(summary.getIsLead()).isTrue();
  }

  @Test
  void givenApplicationWithLeadId_whenToResponse_thenIsLeadFalse() {
    ApplicationReadModel app =
        ApplicationReadModel.builder()
            .applicationId(UUID.randomUUID())
            .modifiedAt(Instant.now())
            .leadApplicationId(UUID.randomUUID())
            .individuals(List.of())
            .build();

    ApplicationSummary summary =
        mapper.toResponse(new FindAllApplicationsResult(List.of(app), Map.of(), 1L, 1, 20))
            .getBody()
            .getApplications()
            .get(0);

    assertThat(summary.getIsLead()).isFalse();
  }

  @Test
  void givenApplicationWithClientIndividual_whenToResponse_thenClientFieldsPopulated() {
    ApplicationIndividual client =
        new ApplicationIndividual(
            UUID.randomUUID(), "Ada", "Lovelace", LocalDate.of(1815, 12, 10), Map.of(), "CLIENT");
    ApplicationReadModel app =
        ApplicationReadModel.builder()
            .applicationId(UUID.randomUUID())
            .modifiedAt(Instant.now())
            .individuals(List.of(client))
            .build();

    ApplicationSummary summary =
        mapper.toResponse(new FindAllApplicationsResult(List.of(app), Map.of(), 1L, 1, 20))
            .getBody()
            .getApplications()
            .get(0);

    assertThat(summary.getClientFirstName()).isEqualTo("Ada");
    assertThat(summary.getClientLastName()).isEqualTo("Lovelace");
    assertThat(summary.getClientDateOfBirth()).isEqualTo(LocalDate.of(1815, 12, 10));
  }

  @Test
  void givenLeadApplicationWithGroup_whenToResponse_thenLinkedApplicationsExcludeSelf() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    ApplicationReadModel leadApp =
        ApplicationReadModel.builder()
            .applicationId(leadId)
            .modifiedAt(Instant.now())
            .leadApplicationId(null)
            .individuals(List.of())
            .build();

    LinkedApplicationGroupReadModel group =
        LinkedApplicationGroupReadModel.builder()
            .groupId(UUID.randomUUID())
            .leadApplicationId(leadId)
            .memberIds(new ArrayList<>(List.of(leadId, memberId)))
            .createdAt(Instant.now())
            .modifiedAt(Instant.now())
            .build();

    ApplicationSummary summary =
        mapper.toResponse(
                new FindAllApplicationsResult(
                    List.of(leadApp), Map.of(leadId, group), 1L, 1, 20))
            .getBody()
            .getApplications()
            .get(0);

    assertThat(summary.getLinkedApplications()).hasSize(1);
    assertThat(summary.getLinkedApplications().get(0).getApplicationId()).isEqualTo(memberId);
    assertThat(summary.getLinkedApplications().get(0).getIsLead()).isFalse();
  }

  @Test
  void givenApplicationWithNoGroup_whenToResponse_thenLinkedApplicationsEmpty() {
    ApplicationReadModel app =
        ApplicationReadModel.builder()
            .applicationId(UUID.randomUUID())
            .modifiedAt(Instant.now())
            .individuals(List.of())
            .build();

    ApplicationSummary summary =
        mapper.toResponse(new FindAllApplicationsResult(List.of(app), Map.of(), 1L, 1, 20))
            .getBody()
            .getApplications()
            .get(0);

    assertThat(summary.getLinkedApplications()).isEmpty();
  }
}
