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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.Opponent;
import uk.gov.justice.laa.dstew.access.model.Provider;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationCreateRequestGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;

@ExtendWith(MockitoExtension.class)
class ApplicationMapperTest extends BaseMapperTest {

    @InjectMocks
    private ApplicationMapperImpl applicationMapper;

    @Test
    void givenNullApplicationEntity_whenToApplication_thenReturnsNull() {
        assertThat(applicationMapper.toApplication(null)).isNull();
    }

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
        String officeCode = "officeCode";

        ApplicationEntity entity = ApplicationEntity.builder()
                .id(id)
                .status(status)
                .laaReference(laaReference)
                .schemaVersion(schemaVersion)
                .applicationContent(applicationContent)
                .createdAt(createdAt)
                .modifiedAt(updatedAt)
                .individuals(individuals)
                .officeCode(officeCode)
                .build();

        Application result = applicationMapper.toApplication(entity);

        assertThat(result).isNotNull();
        assertThat(result.getApplicationId()).isEqualTo(id);
        assertThat(result.getLaaReference()).isEqualTo(laaReference);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getLastUpdated()).isEqualTo(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
        assertProviderEquals(result.getProvider(), officeCode, null);
    }

    @Test
    void givenApplicationEntityWithAllNullFields_whenToApplication_thenNullableFieldsAreNull() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .caseworker(null)
                .submittedAt(null)
                .linkedApplications(null)
                .usedDelegatedFunctions(null)
                .isAutoGranted(null)
                .decision(null)
                .individuals(Set.of())
                .build();

        Application result = applicationMapper.toApplication(entity);

        assertThat(result.getAssignedTo()).isNull();
        assertThat(result.getSubmittedAt()).isNull();
        assertThat(result.getIsLead()).isFalse();
        assertThat(result.getUsedDelegatedFunctions()).isNull();
        assertThat(result.getAutoGrant()).isNull();
        assertThat(result.getOverallDecision()).isNull();
    }

    @Test
    void givenApplicationWithCaseworker_whenToApplication_thenAssignedToIsMapped() {
        UUID caseworkerId = UUID.randomUUID();
        CaseworkerEntity caseworker = CaseworkerEntity.builder().id(caseworkerId).build();

        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .caseworker(caseworker)
                .individuals(Set.of())
                .build();

        assertThat(applicationMapper.toApplication(entity).getAssignedTo()).isEqualTo(caseworkerId);
    }

    @Test
    void givenApplicationWithLinkedApplications_whenToApplication_thenIsLeadIsTrue() {
        ApplicationEntity linked = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .individuals(Set.of())
                .build();

        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .linkedApplications(Set.of(linked))
                .individuals(Set.of())
                .build();

        assertThat(applicationMapper.toApplication(entity).getIsLead()).isTrue();
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

        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .applicationContent(content)
                .build();

        Application result = applicationMapper.toApplication(entity);

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

        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .applicationContent(content)
                .build();

        assertThat(applicationMapper.toApplication(entity).getOpponents()).isEmpty();
    }

    @Test
    void givenApplicationWithNullContent_whenToApplication_thenOpponentsIsNull() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .applicationContent(null)
                .build();

        assertThat(applicationMapper.toApplication(entity).getOpponents()).isNull();
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

        ApplicationEntity entity = ApplicationEntity.builder()
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .applicationContent(content)
                .build();

        Opponent mapped = applicationMapper.toApplication(entity).getOpponents().getFirst();
        assertThat(mapped.getFirstName()).isNull();
        assertThat(mapped.getLastName()).isEqualTo("Smith");
        assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
    }

    @Test
    void givenApplicationWithContactEmailOnly_whenToApplication_thenMapsProviderWithContactEmail() {
        ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.officeCode(null));

        assertProviderEquals(applicationMapper.toApplication(entity).getProvider(), null, "test@example.com");
    }

    @Test
    void givenApplicationWithoutContactEmail_whenToApplication_thenProviderHasNullContactEmail() {
        ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.applicationContent(Map.of("otherField", "value")));

        assertProviderEquals(applicationMapper.toApplication(entity).getProvider(), "officeCode", null);
    }

    @Test
    void givenApplicationWithNoProviderData_whenToApplication_thenProviderIsNull() {
        ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                builder -> builder.officeCode(null).applicationContent(Map.of()));

        assertProviderEquals(applicationMapper.toApplication(entity).getProvider(), null, null);
    }

    @Test
    void givenNullApplicationCreateRequest_whenToApplicationEntity_thenReturnNull() {
        assertThat(applicationMapper.toApplicationEntity(null)).isNull();
    }

    @Test
    void givenApplicationCreateRequest_whenToApplicationEntity_thenMapsFieldsCorrectly() {
        ApplicationStatus status = ApplicationStatus.APPLICATION_SUBMITTED;
        String laaReference = "laa_reference";
        List<Individual> expectedIndividuals = List.of(
                Individual.builder().build(),
                Individual.builder().build()
        );

        ApplicationCreateRequest request = DataGenerator.createDefault(ApplicationCreateRequestGenerator.class,
                builder -> builder
                        .status(status)
                        .laaReference(laaReference)
                        .individuals(expectedIndividuals));

        ApplicationEntity result = applicationMapper.toApplicationEntity(request);

        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getLaaReference()).isEqualTo(laaReference);
        assertThat(result.getIndividuals())
                .isNotNull()
                .hasSize(expectedIndividuals.size())
                .allSatisfy(individual -> assertThat(individual).isInstanceOf(IndividualEntity.class));
        assertThat(result.getApplicationContent())
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(request.getApplicationContent());
    }

    @Test
    void givenApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperUpdatesRelevantFields() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .applicationContent(Map.of("key", "value"))
                .createdAt(Instant.now().minusSeconds(10000))
                .modifiedAt(Instant.now().minusSeconds(5000))
                .build();

        ApplicationStatus updatedStatus = ApplicationStatus.APPLICATION_SUBMITTED;
        Map<String, Object> updatedContent = Map.of("newKey", "newValue");

        applicationMapper.updateApplicationEntity(entity, ApplicationUpdateRequest.builder()
                .status(updatedStatus)
                .applicationContent(updatedContent)
                .build());

        assertThat(entity.getStatus()).isEqualTo(updatedStatus);
        assertThat(entity.getApplicationContent()).isEqualTo(updatedContent);
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
