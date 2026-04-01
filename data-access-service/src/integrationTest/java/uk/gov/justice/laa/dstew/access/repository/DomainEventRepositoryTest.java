package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

class DomainEventRepositoryTest extends BaseIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void givenDomainEventEntity_whenSaved_thenPersistedWithCorrectJsonData() {

    // given
    ApplicationEntity existing = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    String jsonData = """
            {
              "applicationId": "11111111-1111-1111-1111-111111111111",
              "applicationStatus": "APPLICATION_IN_PROGRESS",
              "applicationContent": "{\\"foo\\":\\"bar\\"}",
              "createdAt": "2025-01-01T10:00:00Z",
              "createdBy": null
            }
            """;

    DomainEventEntity expected = DataGenerator.createDefault(DomainEventGenerator.class, builder ->
        builder
            .applicationId(existing.getId())
            .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
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

    assertThat(jsonNode.get("applicationId").asString())
        .isEqualTo("11111111-1111-1111-1111-111111111111");

    assertThat(jsonNode.get("applicationStatus").asString())
        .isEqualTo("APPLICATION_IN_PROGRESS");

    assertThat(jsonNode.get("applicationContent").asString())
        .contains("foo");

    assertThat(jsonNode.get("createdBy").isNull()).isTrue();
  }

  @Test
  void givenApplicationUpdatedDomainEvent_whenSaved_thenPersistedWithCorrectJsonData() {

    // given
    ApplicationEntity existing = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    String jsonData = """
        {
          "applicationId": "22222222-2222-2222-2222-222222222222",
          "applicationStatus": "APPLICATION_SUBMITTED",
          "applicationContent": "{\\"bar\\":\\"baz\\"}",
          "updatedAt": "2025-02-01T12:30:00Z",
          "updatedBy": "caseworkerResponse-123"
        }
        """;

    DomainEventEntity expected = DataGenerator.createDefault(DomainEventGenerator.class, builder ->
        builder
            .applicationId(existing.getId())
            .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
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

    assertThat(jsonNode.get("applicationId").asString())
        .isEqualTo("22222222-2222-2222-2222-222222222222");

    assertThat(jsonNode.get("applicationStatus").asString())
        .isEqualTo("APPLICATION_SUBMITTED");

    assertThat(jsonNode.get("applicationContent").asString())
        .contains("bar");

    assertThat(jsonNode.get("updatedAt").asString())
        .isEqualTo("2025-02-01T12:30:00Z");

    assertThat(jsonNode.get("updatedBy").asString())
        .isEqualTo("caseworkerResponse-123");
  }
}
