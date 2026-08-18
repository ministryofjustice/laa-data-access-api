package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class AssignCaseworkerRequestMapperTest {

  private final AssignCaseworkerRequestMapper mapper =
      new AssignCaseworkerRequestMapper(JsonMapper.builder().build());

  @Test
  void givenOneApplication_whenMapped_thenMapsAssignmentAndAuditData() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CaseworkerAssignRequest request =
        CaseworkerAssignRequest.builder()
            .caseworkerId(caseworkerId)
            .applicationIds(List.of(applicationId))
            .eventHistory(EventHistoryRequest.builder().eventDescription("Assigned").build())
            .build();

    var result = mapper.toAssignment(request);

    assertThat(result.caseworkerId()).isEqualTo(caseworkerId);
    assertThat(result.applicationId()).isEqualTo(applicationId);
    assertThat(result.eventDescription()).isEqualTo("Assigned");
    assertThat(result.serialisedRequest())
        .contains(caseworkerId.toString(), applicationId.toString(), "Assigned");
  }

  @Test
  void givenNoApplications_whenMapped_thenRejectsRequest() {
    CaseworkerAssignRequest request =
        CaseworkerAssignRequest.builder()
            .caseworkerId(UUID.randomUUID())
            .applicationIds(List.of())
            .build();

    assertThatThrownBy(() -> mapper.toAssignment(request))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly("Exactly one application ID must be provided"));
  }

  @Test
  void givenMultipleApplications_whenMapped_thenRejectsRequest() {
    CaseworkerAssignRequest request =
        CaseworkerAssignRequest.builder()
            .caseworkerId(UUID.randomUUID())
            .applicationIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
            .build();

    assertThatThrownBy(() -> mapper.toAssignment(request))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly("Exactly one application ID must be provided"));
  }
}
