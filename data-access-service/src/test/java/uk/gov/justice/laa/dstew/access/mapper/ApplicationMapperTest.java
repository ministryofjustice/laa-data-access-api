package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;

class ApplicationMapperTest {

  private final ApplicationMapper applicationMapper = Mappers.getMapper(ApplicationMapper.class);

    @Test
    void givenApplicationEntity_whenToApplication_thenMapsFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        ApplicationStatus status = ApplicationStatus.APPLICATION_SUBMITTED;
        String laaReference = "Ref456";
        int schemaVersion = 2;
        Map<String, Object> applicationContent = Map.of("key1", "value1", "key2", 456);
        Instant createdAt = Instant.now().minusSeconds(600000);
        Instant updatedAt = Instant.now();
        Set<IndividualEntity> individuals = Set.of();

        ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
                .id(id)
                .status(status)
                .laaReference(laaReference)
                .schemaVersion(schemaVersion)
                .applicationContent(applicationContent)
                .createdAt(createdAt)
                .modifiedAt(updatedAt)
                .individuals(individuals)
                .build();

        Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);

        assertThat(actualApplication).isNotNull();
        assertThat(actualApplication.getApplicationId()).isEqualTo(id);
        assertThat(actualApplication.getLaaReference()).isEqualTo(laaReference);
        assertThat(actualApplication.getStatus()).isEqualTo(status);
        assertThat(actualApplication.getLastUpdated()).isEqualTo(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
    }


    @Test
    void givenNullApplicationEntity_whenToApplication_thenReturnsNull() {
        ApplicationEntity entity = null;
        assertThat(applicationMapper.toApplication(entity)).isNull();
    }

    @Test
    void givenApplicationWithNullCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithNullCaseworker() {
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();
        Set<IndividualEntity> individuals = Set.of();
        CaseworkerEntity caseworker = null;

        ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .caseworker(caseworker)
                .individuals(individuals)
                .build();

        Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
        assertThat(actualApplication.getCaseworkerId()).isNull();
    }

    @Test
    void givenApplicationWithCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithCaseworker() {
        UUID caseworkerId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();
        Set<IndividualEntity> individuals = Set.of();

        CaseworkerEntity caseworker = CaseworkerEntity.builder().id(caseworkerId).build();

        ApplicationEntity expectedApplicationEntity = ApplicationEntity.builder()
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .caseworker(caseworker)
                .individuals(individuals)
                .build();

        Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
        assertThat(actualApplication.getCaseworkerId()).isEqualTo(caseworkerId);
    }

    @Test
    void givenApplicationCreateRequest_whenToApplicationEntity_thenMapsFieldsCorrectly() {
        ApplicationStatus status = ApplicationStatus.APPLICATION_SUBMITTED;
    UUID applicationContentId = UUID.randomUUID();

    RequestApplicationContent requestApplicationContent =
        RequestApplicationContent.builder()
            .applicationReference("LXB-111-111")
            .applicationContent(ApplicationContent.builder()
                .id(applicationContentId)
                .proceedings(List.of(
                    Proceeding.builder()
                        .leadProceeding(true)
                        .categoryOfLaw("Crime")
                        .matterType("Defence")
                        .usedDelegatedFunctions(true)
                        .description("Test proceeding")
                        .build()
                )).build()
            ).build();
    String laaReference = "laa_reference";
    List<Individual> expectedIndividuals = List.of(
        Individual.builder().build(),
        Individual.builder().build()
    );

    ApplicationCreateRequest expectedApplicationCreateRequest = ApplicationCreateRequest.builder()
        .status(status)
        .applicationContent(MapperUtil.getObjectMapper().convertValue(requestApplicationContent, Map.class))
        .laaReference(laaReference)
        .individuals(expectedIndividuals)
        .build();


    ApplicationEntity actualApplicationEntity = applicationMapper.toApplicationEntity(expectedApplicationCreateRequest);

    assertThat(actualApplicationEntity.getStatus()).isEqualTo(status);
    assertThat(actualApplicationEntity.getLaaReference()).isEqualTo(laaReference);

    assertThat(actualApplicationEntity.getIndividuals())
        .isNotNull()
        .hasSize(expectedIndividuals.size())
        .allSatisfy(individual -> assertThat(individual).isInstanceOf(IndividualEntity.class));

    assertThat(actualApplicationEntity.getApplicationContent())
        .isNotNull()
        .usingRecursiveComparison()
        .isEqualTo(MapperUtil.getObjectMapper().convertValue(requestApplicationContent, Map.class));
  }

  @Test
  void givenNullApplicationCreateRequest_whenToApplicationEntity_thenReturnNull() {
    ApplicationCreateRequest request = null;
    assertThat(applicationMapper.toApplicationEntity(request)).isNull();
  }

    @Test
    void givenEmptyApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperOnlyUpdatesMandatoryFields() {
        ApplicationStatus initialStatus = ApplicationStatus.APPLICATION_IN_PROGRESS;
        Map<String, Object> initialContent = Map.of("key", "value");
        Instant createdAt = Instant.now().minusSeconds(10000);
        Instant modifiedAt = Instant.now().minusSeconds(5000);

    ApplicationEntity entityToAffect = ApplicationEntity.builder()
        .status(initialStatus)
        .applicationContent(initialContent)
        .createdAt(createdAt)
        .modifiedAt(modifiedAt)
        .build();

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls except applicationContent
    applicationMapper.updateApplicationEntity(entityToAffect, req);

    assertThat(entityToAffect.getStatus()).isEqualTo(initialStatus);
    assertThat(entityToAffect.getApplicationContent()).isNotNull().hasSize(0);
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }

    @Test
    void givenApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperUpdatesRelevantFields() {
        ApplicationStatus initialStatus = ApplicationStatus.APPLICATION_IN_PROGRESS;
        Map<String, Object> initialContent = Map.of("key", "value");
        Instant createdAt = Instant.now().minusSeconds(10000);
        Instant modifiedAt = Instant.now().minusSeconds(5000);

    ApplicationEntity entityToAffect = ApplicationEntity.builder()
        .status(initialStatus)
        .applicationContent(initialContent)
        .createdAt(createdAt)
        .modifiedAt(modifiedAt)
        .build();

        ApplicationStatus updatedStatus = ApplicationStatus.APPLICATION_SUBMITTED;
        Map<String, Object> updatedContent = Map.of("newKey", "newValue");

    ApplicationUpdateRequest applicationUpdateRequest = ApplicationUpdateRequest.builder()
        .status(updatedStatus)
        .applicationContent(updatedContent)
        .build();

    applicationMapper.updateApplicationEntity(entityToAffect, applicationUpdateRequest);

    assertThat(entityToAffect.getStatus()).isEqualTo(updatedStatus);
    assertThat(entityToAffect.getApplicationContent()).isEqualTo(updatedContent);
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }
}