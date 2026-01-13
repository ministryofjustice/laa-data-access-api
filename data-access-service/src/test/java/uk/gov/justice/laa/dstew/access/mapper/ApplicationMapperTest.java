package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
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
    entity.setLaaReference("Ref123");
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("foo", "bar", "baz", 123));
    entity.setCreatedAt(createdAt);
    entity.setModifiedAt(updatedAt);

    Application result = applicationMapper.toApplication(entity);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getLaaReference()).isEqualTo("Ref123");
    assertThat(result.getStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
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
        .laaReference("laa_reference_1")
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
  void shouldMapApplicationEntityCaseworkerNullToApplication() {
    ApplicationEntity entity = ApplicationEntity.builder()
                                                .applicationContent(Map.of("foo", "bar"))
                                                .createdAt(Instant.now())
                                                .modifiedAt(Instant.now())
                                                .caseworker(null)
                                                .build();
    var result = applicationMapper.toApplication(entity);
    assertThat(result.getCaseworkerId()).isNull();
  }

  @Test
  void shouldMapApplicationEntityCaseworkerToApplication() {
    final UUID caseworkerId = UUID.randomUUID();
    ApplicationEntity entity = ApplicationEntity.builder()
                                                .applicationContent(Map.of("foo", "bar"))
                                                .createdAt(Instant.now())
                                                .modifiedAt(Instant.now())
                                                .caseworker(CaseworkerEntity.builder().id(caseworkerId).build())
                                                .build();
    var result = applicationMapper.toApplication(entity);
    assertThat(result.getCaseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  void shouldMapApplicationCreateRequestToApplicationEntity() {
    ApplicationCreateRequest req = ApplicationCreateRequest.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .laaReference("laa_reference")
        .build();

    ApplicationEntity result = applicationMapper.toApplicationEntity(req);

    assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
    assertThat(result.getLaaReference()).isEqualTo("laa_reference");
  }

  @Test
  void shouldMapApplicationCreateRequestToIndividuals() {
    Individual individual = Individual.builder()
                                      .firstName("John")
                                      .lastName("Doe")
                                      .dateOfBirth(LocalDate.of(2025, 11, 24))
                                      .details(Map.of("foo", "bar"))
                                      .build();
    ApplicationCreateRequest req = ApplicationCreateRequest.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .laaReference("laa_reference")
        .individuals(List.of(individual))
        .build();
    ApplicationEntity result = applicationMapper.toApplicationEntity(req);
    assertThat(result.getIndividuals()).hasSize(1);
    var mappedIndividual = result.getIndividuals()
                                 .stream()
                                 .findFirst()
                                 .get();
    assertThat(mappedIndividual.getFirstName()).isEqualTo("John");
    assertThat(mappedIndividual.getLastName()).isEqualTo("Doe");
    assertThat(mappedIndividual.getDateOfBirth()).isEqualTo(LocalDate.of(2025, 11, 24));
    assertThat(mappedIndividual.getIndividualContent()).isEqualTo(Map.of("foo", "bar"));
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
}
