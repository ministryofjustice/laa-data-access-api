package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;

class ApplicationMapperTest {

  private final ApplicationMapper applicationMapper = Mappers.getMapper(ApplicationMapper.class);

  @Test
  void givenApplicationEntity_whenToApplication_thenMapsFieldsCorrectly() {

    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.now().minusSeconds(600000);
    Instant updatedAt = Instant.now();
    ApplicationEntity expectedApplicationEntity = new ApplicationEntity();
    expectedApplicationEntity.setId(id);
    expectedApplicationEntity.setStatus(ApplicationStatus.SUBMITTED);
    expectedApplicationEntity.setLaaReference("Ref456");
    expectedApplicationEntity.setSchemaVersion(2);
    expectedApplicationEntity.setApplicationContent(Map.of("key1", "value1", "key2", 456));
    expectedApplicationEntity.setCreatedAt(createdAt);
    expectedApplicationEntity.setModifiedAt(updatedAt);
    expectedApplicationEntity.setIndividuals(Set.of());

    Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);

    assertThat(actualApplication).isNotNull();
    assertThat(actualApplication.getId()).isEqualTo(expectedApplicationEntity.getId());
    assertThat(actualApplication.getLaaReference()).isEqualTo(expectedApplicationEntity.getLaaReference());
    assertThat(actualApplication.getApplicationStatus()).isEqualTo(expectedApplicationEntity.getStatus());
    assertThat(actualApplication.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(expectedApplicationEntity.getCreatedAt(), ZoneOffset.UTC));
    assertThat(actualApplication.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(expectedApplicationEntity.getUpdatedAt(), ZoneOffset.UTC));
    assertThat(actualApplication.getApplicationContent())
        .isNotNull()
        .usingRecursiveComparison()
        .isEqualTo(expectedApplicationEntity.getApplicationContent());
  }

  @Test
  void givenApplicationEntityWithIndividuals_whenToApplication_thenMapsSetCorrectly() {

    Set<IndividualEntity> expectedIndividuals = Set.of(
        IndividualEntity.builder().id(UUID.randomUUID()).build(),
        IndividualEntity.builder().id(UUID.randomUUID()).build()
    );

    ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
        .individuals(expectedIndividuals)
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .build();

    Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);

    assertThat(actualApplication.getIndividuals())
        .isNotNull()
        .hasSize(2)
        .allSatisfy(individual -> assertThat(individual).isInstanceOf(Individual.class));
  }

  @Test
  void givenNullApplicationEntity_whenToApplication_thenReturnsNull() {

    assertThat(applicationMapper.toApplication(null)).isNull();
  }

  @Test
  void givenApplicationWithNullCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithNullCaseworker() {

    ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
                                            .createdAt(Instant.now())
                                            .modifiedAt(Instant.now())
                                            .caseworker(null)
                                            .individuals(Set.of())
                                            .build();

    var actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
    assertThat(actualApplication.getCaseworkerId()).isNull();
  }

  @Test
  void givenApplicationWithCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithCaseworker() {
    final UUID caseworkerId = UUID.randomUUID();
    ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
                                                .createdAt(Instant.now())
                                                .modifiedAt(Instant.now())
                                                .caseworker(CaseworkerEntity.builder().id(caseworkerId).build())
                                                .individuals(Set.of())
                                                .build();

    var actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
    assertThat(actualApplication.getCaseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  void givenApplicationCreateRequest_whenToApplicationEntity_thenMapsFieldsCorrectly() {

      List<Individual> expectedIndividuals = List.of(
                Individual.builder().build(),
                Individual.builder().build());

      ApplicationCreateRequest expectedApplicationCreateRequest = ApplicationCreateRequest.builder()
        .status(ApplicationStatus.SUBMITTED)
        .applicationContent(Map.of("foo", "bar"))
        .laaReference("laa_reference")
        .individuals(expectedIndividuals)
        .build();

      ApplicationEntity actualApplicationEntity = applicationMapper.toApplicationEntity(expectedApplicationCreateRequest);

      assertThat(actualApplicationEntity.getStatus()).isEqualTo(expectedApplicationCreateRequest.getStatus());
      assertThat(actualApplicationEntity.getLaaReference()).isEqualTo(expectedApplicationCreateRequest.getLaaReference());

      assertThat(actualApplicationEntity.getIndividuals())
                .isNotNull()
                .hasSize(expectedIndividuals.size())
                .allSatisfy(individual -> assertThat(individual).isInstanceOf(IndividualEntity.class));

      assertThat(actualApplicationEntity.getApplicationContent())
          .isNotNull()
          .usingRecursiveComparison()
          .isEqualTo(expectedApplicationCreateRequest.getApplicationContent());
  }

  @Test
  void givenNullApplicationCreateRequest_whenToApplicationEntity_thenReturnNull() {
    assertThat(applicationMapper.toApplicationEntity(null)).isNull();
  }

  @Test
  void givenEmptyApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperOnlyUpdatesMandatoryFields() {
    Instant createdAt = Instant.now().minusSeconds(10000);
    Instant modifiedAt = Instant.now().minusSeconds(5000);

      ApplicationEntity entityToAffect = ApplicationEntity.builder()
        .status(ApplicationStatus.IN_PROGRESS)
        .applicationContent(Map.of("key", "value"))
        .createdAt(createdAt)
        .modifiedAt(modifiedAt)
        .build();

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls except applicationContent
    applicationMapper.updateApplicationEntity(entityToAffect, req);

    // no changes expected
    assertThat(entityToAffect.getStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
    assertThat(entityToAffect.getApplicationContent()).isNotNull().hasSize(0);
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }

  @Test
  void givenApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperUpdatesRelevantFields() {
      Instant createdAt = Instant.now().minusSeconds(10000);
      Instant modifiedAt = Instant.now().minusSeconds(5000);

      ApplicationEntity entityToAffect = ApplicationEntity.builder()
              .status(ApplicationStatus.IN_PROGRESS)
              .applicationContent(Map.of("key", "value"))
              .createdAt(createdAt)
              .modifiedAt(modifiedAt)
              .build();

    ApplicationUpdateRequest applicationUpdateRequest = ApplicationUpdateRequest.builder()
                    .status(ApplicationStatus.SUBMITTED)
                    .applicationContent(Map.of("newKey", "newValue"))
                    .build();

    applicationMapper.updateApplicationEntity(entityToAffect, applicationUpdateRequest);

    assertThat(entityToAffect.getStatus()).isEqualTo(applicationUpdateRequest.getStatus());
    assertThat(entityToAffect.getApplicationContent()).isEqualTo(applicationUpdateRequest.getApplicationContent());
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }
}
