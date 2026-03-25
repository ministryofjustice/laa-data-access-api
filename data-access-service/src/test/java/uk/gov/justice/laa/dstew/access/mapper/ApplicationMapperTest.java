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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequestIndividual;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.model.Opponent;
import uk.gov.justice.laa.dstew.access.model.Provider;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationUpdateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

@ExtendWith(MockitoExtension.class)
public class ApplicationMapperTest extends BaseMapperTest {

  @InjectMocks
  private ApplicationMapperImpl applicationMapper;


  @Test
  void givenApplicationEntity_whenToApplication_thenMapsFieldsCorrectly() {
    UUID id = UUID.randomUUID();
    ApplicationStatus status = ApplicationStatus.APPLICATION_SUBMITTED;
    String laaReference = "Ref456";
    int schemaVersion = 2;
    Instant createdAt = Instant.now().minusSeconds(600000);
    Instant updatedAt = Instant.now();
    String officeCode = "officeCode";

    ApplicationEntity expectedApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .id(id)
        .status(status)
        .laaReference(laaReference)
        .schemaVersion(schemaVersion)
        .createdAt(createdAt)
        .modifiedAt(updatedAt)
        .individuals(Set.of())
        .officeCode(officeCode)
        .applicationContent(Map.of()));

    Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);

    assertThat(actualApplication).isNotNull();
    assertThat(actualApplication.getApplicationId()).isEqualTo(id);
    assertThat(actualApplication.getLaaReference()).isEqualTo(laaReference);
    assertThat(actualApplication.getStatus()).isEqualTo(status);
    assertThat(actualApplication.getLastUpdated()).isEqualTo(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
    assertProviderEquals(actualApplication.getProvider(), officeCode, null);
  }


  @Test
  void givenNullApplicationEntity_whenToApplication_thenReturnsNull() {
    ApplicationEntity entity = null;
    assertThat(applicationMapper.toApplication(entity)).isNull();
  }

  @Test
  void givenApplicationWithNullCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithNullCaseworker() {
    ApplicationEntity expectedApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .caseworker(null));

    Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
    assertThat(actualApplication.getAssignedTo()).isNull();
  }

  @Test
  void givenApplicationWithCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithCaseworker() {
    UUID caseworkerId = UUID.randomUUID();

    CaseworkerEntity caseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder
        .id(caseworkerId));

    ApplicationEntity expectedApplicationEntity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .caseworker(caseworker));

    Application actualApplication = applicationMapper.toApplication(expectedApplicationEntity);
    assertThat(actualApplication.getAssignedTo()).isEqualTo(caseworkerId);
  }

  @Test
  void givenApplicationCreateRequest_whenToApplicationEntity_thenMapsFieldsCorrectly() {
    ApplicationStatus status = ApplicationStatus.APPLICATION_SUBMITTED;
    String laaReference = "laa_reference";
    List<ApplicationCreateRequestIndividual> expectedIndividuals = List.of(
        ApplicationCreateRequestIndividual.builder().build(),
        ApplicationCreateRequestIndividual.builder().build()
    );

    ApplyApplication applyApplication = new ApplyApplication();
    applyApplication.setObjectType("apply");
    applyApplication.setId(UUID.randomUUID());
    applyApplication.setSubmittedAt(OffsetDateTime.now());

    ApplicationCreateRequest expectedApplicationCreateRequest = DataGenerator.createDefault(
        ApplicationCreateRequestGenerator.class, builder -> builder
            .status(status)
            .laaReference(laaReference)
            .individuals(expectedIndividuals)
            .applicationContent(applyApplication));

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
        .isEqualTo(MapperUtil.getObjectMapper().convertValue(applyApplication, Map.class));
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

    ApplicationEntity entityToAffect = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .status(initialStatus)
        .applicationContent(initialContent)
        .createdAt(createdAt)
        .modifiedAt(modifiedAt));

    ApplicationUpdateRequest req = DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class, builder -> builder
        .status(null)
        .applicationContent(null));

    applicationMapper.updateApplicationEntity(entityToAffect, req);

    assertThat(entityToAffect.getStatus()).isEqualTo(initialStatus);
    assertThat(entityToAffect.getApplicationContent()).isNull();
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }

  @Test
  void givenApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperUpdatesRelevantFields() {
    ApplicationStatus initialStatus = ApplicationStatus.APPLICATION_IN_PROGRESS;
    Map<String, Object> initialContent = Map.of("key", "value");
    Instant createdAt = Instant.now().minusSeconds(10000);
    Instant modifiedAt = Instant.now().minusSeconds(5000);

    ApplicationEntity entityToAffect = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .status(initialStatus)
        .applicationContent(initialContent)
        .createdAt(createdAt)
        .modifiedAt(modifiedAt));

    ApplicationStatus updatedStatus = ApplicationStatus.APPLICATION_SUBMITTED;
    Map<String, Object> updatedContent = Map.of("newKey", "newValue");

    ApplicationUpdateRequest applicationUpdateRequest = DataGenerator.createDefault(ApplicationUpdateRequestGenerator.class, builder -> builder
        .status(updatedStatus)
        .applicationContent(updatedContent));

    applicationMapper.updateApplicationEntity(entityToAffect, applicationUpdateRequest);

    assertThat(entityToAffect.getStatus()).isEqualTo(updatedStatus);
    assertThat(entityToAffect.getApplicationContent()).isEqualTo(updatedContent);
    assertThat(entityToAffect.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entityToAffect.getModifiedAt()).isEqualTo(modifiedAt);
  }

  @Test
  void givenApplicationWithOpponents_whenToApplication_thenMapsOpponentsCorrectly() {
    Map<String, Object> opposable = Map.of(
        "opposableType", "ApplicationMeritsTask::Individual",
        "firstName", "John",
        "lastName", "Smith",
        "name", "Acme Ltd"
    );
    Map<String, Object> content = Map.of(
        "applicationMerits", Map.of("opponents", List.of(Map.of("opposable", opposable)))
    );

    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .applicationContent(content));

    Application result = applicationMapper.toApplication(entity);

    assertThat(result.getOpponents()).isNotNull();
    assertThat(result.getOpponents()).hasSize(1);

    Opponent mapped = result.getOpponents().getFirst();
    assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    assertThat(mapped.getFirstName()).isEqualTo("John");
    assertThat(mapped.getLastName()).isEqualTo("Smith");
    assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }

  @Test
  void givenApplicationWithEmptyOpponentsList_whenToApplication_thenReturnsEmptyList() {
    Map<String, Object> content = Map.of(
        "applicationMerits", Map.of("opponents", List.of())
    );

    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .applicationContent(content));

    Application result = applicationMapper.toApplication(entity);

    assertThat(result.getOpponents()).isNotNull();
    assertThat(result.getOpponents()).isEmpty();
  }

  @Test
  void givenOpponentWithMissingFirstName_whenToApplication_thenMapsRemainingFields() {
    Map<String, Object> opposable = Map.of(
        "opposableType", "ApplicationMeritsTask::Individual",
        "lastName", "Smith",
        "name", "Acme Ltd"
    );
    Map<String, Object> content = Map.of(
        "applicationMerits", Map.of("opponents", List.of(Map.of("opposable", opposable)))
    );

    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .applicationContent(content));

    Application result = applicationMapper.toApplication(entity);

    assertThat(result.getOpponents()).isNotNull();
    assertThat(result.getOpponents()).hasSize(1);

    var mapped = result.getOpponents().getFirst();

    assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    assertThat(mapped.getFirstName()).isNull();
    assertThat(mapped.getLastName()).isEqualTo("Smith");
    assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }

  @Test
  void givenApplicationWithContactEmailOnly_whenToApplication_thenMapsProviderWithContactEmail() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .officeCode(null));

    Application result = applicationMapper.toApplication(entity);

    assertProviderEquals(result.getProvider(), null, "test@example.com");
  }

  @Test
  void givenApplicationWithoutContactEmail_whenToApplication_thenProviderHasNullContactEmail() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .applicationContent(Map.of("otherField", "value")));

    Application result = applicationMapper.toApplication(entity);

    assertProviderEquals(result.getProvider(), "officeCode", null);
  }

  @Test
  void givenApplicationWithNullApplicationContent_whenToApplication_thenProviderHasOfficeCodeOnly() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .applicationContent(null));

    Application result = applicationMapper.toApplication(entity);

    assertProviderEquals(result.getProvider(), "officeCode", null);
  }

  @Test
  void givenApplicationWithNoProviderData_whenToApplication_thenProviderIsNull() {
    ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder -> builder
        .officeCode(null)
        .applicationContent(Map.of()));

    Application result = applicationMapper.toApplication(entity);

    assertProviderEquals(result.getProvider(), null, null);
  }

  private void assertProviderEquals(Provider actual, String expectedOfficeCode, String expectedContactEmail) {
    if (expectedOfficeCode == null && expectedContactEmail == null) {
      assertThat(actual).isNull();
    } else {
      assertThat(actual).isNotNull();
      assertThat(actual.getOfficeCode()).isEqualTo(expectedOfficeCode);
      assertThat(actual.getContactEmail()).isEqualTo(expectedContactEmail);
    }
  }
}