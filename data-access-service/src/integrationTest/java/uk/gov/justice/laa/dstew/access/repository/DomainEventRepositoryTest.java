package uk.gov.justice.laa.dstew.access.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventRepositoryTest extends BaseIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void givenDomainEventEntity_whenSaved_thenPersistedWithCorrectJsonData() throws Exception {

    // given
    String jsonData = """
            {
              "applicationId": "11111111-1111-1111-1111-111111111111",
              "applicationStatus": "IN_PROGRESS",
              "applicationContent": "{\\"foo\\":\\"bar\\"}",
              "createdAt": "2025-01-01T10:00:00Z",
              "createdBy": null
            }
            """;

    DomainEventEntity expected = domainEventFactory.create(builder ->
        builder
            .type(DomainEventType.APPLICATION_CREATED)
            .createdBy(null)
            .data(jsonData)
            .build()
    );

    // when
    domainEventRepository.save(expected);

    // then
    DomainEventEntity actual =
        domainEventRepository.findById(expected.getId()).orElseThrow();

    // DomainEvent assertions
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("createdAt")
        .isEqualTo(expected);

    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getData()).isNotBlank();

    // JSON content assertions
    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode = mapper.readTree(actual.getData());

    assertThat(jsonNode.get("applicationId").asText())
        .isEqualTo("11111111-1111-1111-1111-111111111111");

    assertThat(jsonNode.get("applicationStatus").asText())
        .isEqualTo("IN_PROGRESS");

    assertThat(jsonNode.get("applicationContent").asText())
        .contains("foo");

    assertThat(jsonNode.get("createdBy").isNull()).isTrue();
  }

  @Test
  void givenApplicationUpdatedDomainEvent_whenSaved_thenPersistedWithCorrectJsonData() throws Exception {

    // given
    String jsonData = """
        {
          "applicationId": "22222222-2222-2222-2222-222222222222",
          "applicationStatus": "SUBMITTED",
          "applicationContent": "{\\"bar\\":\\"baz\\"}",
          "updatedAt": "2025-02-01T12:30:00Z",
          "updatedBy": "caseworker-123"
        }
        """;

    DomainEventEntity expected = domainEventFactory.create(builder ->
        builder
            .type(DomainEventType.APPLICATION_UPDATED)
            .createdBy("")
            .data(jsonData)
            .build()
    );

    // when
    domainEventRepository.save(expected);

    // then
    DomainEventEntity actual =
        domainEventRepository.findById(expected.getId()).orElseThrow();

    // entity assertions
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("createdAt")
        .isEqualTo(expected);

    assertThat(actual.getCreatedAt()).isNotNull();
    assertThat(actual.getData()).isNotBlank();

    // JSON assertions
    JsonNode jsonNode = mapper.readTree(actual.getData());

    assertThat(jsonNode.get("applicationId").asText())
        .isEqualTo("22222222-2222-2222-2222-222222222222");

    assertThat(jsonNode.get("applicationStatus").asText())
        .isEqualTo("SUBMITTED");

    assertThat(jsonNode.get("applicationContent").asText())
        .contains("bar");

    assertThat(jsonNode.get("updatedAt").asText())
        .isEqualTo("2025-02-01T12:30:00Z");

    assertThat(jsonNode.get("updatedBy").asText())
        .isEqualTo("caseworker-123");
  }
}
