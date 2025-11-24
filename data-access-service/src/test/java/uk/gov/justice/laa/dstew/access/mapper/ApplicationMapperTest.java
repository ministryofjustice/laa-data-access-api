package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;

class ApplicationMapperTest {

  private ApplicationMapper applicationMapper;

  @BeforeEach
  void setUp() {
    applicationMapper = new ApplicationMapperImpl();
  }

  @Test
  void shouldMapApplicationEntityToApplication() {
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.now().minusSeconds(600000);
    Instant updatedAt = Instant.now();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setApplicationReference("Ref123");
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("foo", "bar", "baz", 123));
    entity.setCreatedAt(createdAt);
    entity.setModifiedAt(updatedAt);

    Application result = applicationMapper.toApplication(entity);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getApplicationReference()).isEqualTo("Ref123");
    assertThat(result.getApplicationStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
    assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    assertThat(result.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
  }

  @Test
  void toApplication_mapsLinkedIndividualsCorrectly() {
    IndividualEntity individual = IndividualEntity.builder()
        .id(UUID.randomUUID())
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.of(1990, 1, 1))
        .individualContent(Map.of("notes", "Test"))
        .build();

    ApplicationEntity applicationEntity = ApplicationEntity.builder()
        .id(UUID.randomUUID())
        .applicationReference("application_reference_1")
        .status(ApplicationStatus.IN_PROGRESS)
        .individuals(Set.of(individual))
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    Application application = applicationMapper.toApplication(applicationEntity);

    assertThat(application.getIndividuals())
        .isNotNull()
        .hasSize(1);

    Individual mapped = application.getIndividuals().get(0);
    assertThat(mapped.getFirstName()).isEqualTo("John");
    assertThat(mapped.getLastName()).isEqualTo("Doe");
    assertThat(mapped.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(mapped.getDetails()).containsEntry("notes", "Test");
  }

  @Test
  void shouldReturnNullWhenMappingNullEntity() {
    assertThat(applicationMapper.toApplication(null)).isNull();
  }

  @Test
  void shouldMapApplicationCreateRequestToApplicationEntity() {
    ApplicationCreateRequest req = ApplicationCreateRequest.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .applicationReference("app_reference")
        .build();

    ApplicationEntity result = applicationMapper.toApplicationEntity(req);

    assertThat(result.getId()).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
    assertThat(result.getApplicationReference()).isEqualTo("app_reference");
  }

  @Test
  void shouldReturnNullWhenMappingNullCreateRequest() {
    assertThat(applicationMapper.toApplicationEntity(null)).isNull();
  }

  @Test
  void shouldUpdateApplicationEntityWithoutOverwritingNulls() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls
    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
  }

  @Test
  void shouldUpdateApplicationEntityWithNewValues() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("oldKey", "oldValue"));

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setStatus(ApplicationStatus.SUBMITTED);
    req.setApplicationContent(Map.of("newKey", "newValue"));

    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    assertThat(entity.getApplicationContent()).containsEntry("newKey", "newValue");
  }

  @Test
  void shouldThrowWhenApplicationCreateRequestContentCannotBeSerialized() {
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatus(ApplicationStatus.IN_PROGRESS);
    req.setApplicationContent(Map.of("key", new Object() {}));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplicationEntity(req));
  }

  @Test
  void shouldThrowWhenApplicationUpdateRequestContentCannotBeSerialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setApplicationContent(Map.of("key", new Object() {}));

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> applicationMapper.updateApplicationEntity(entity, req)
    );

    assertThat(ex.getMessage())
        .isEqualTo("Failed to serialize ApplicationUpdateRequest.applicationContent");
  }

  @Test
  void shouldThrowWhenApplicationEntityContentCannotBeDeserialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setApplicationContent(Map.of("key", new Object() {}));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplication(entity));
  }
}
