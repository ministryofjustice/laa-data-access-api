package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MatterType;


@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

  @InjectMocks
  private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "95bb88f1-99ca-4ecf-b867-659b55a8cf93")
    void givenApplicationSummaryEntity_whenToApplicationSummary_thenMapsFieldsCorrectly(UUID caseworkerId) {
        // Test data setup
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();
        Instant submittedAt = Instant.now();
        boolean autoGranted = true;
        CategoryOfLaw categoryOfLaw = CategoryOfLaw.FAMILY;
        MatterType matterType = MatterType.SCA;
        boolean usedDelegatedFunctions = true;
        String laaReference = "ref1";
        ApplicationStatus status = ApplicationStatus.APPLICATION_IN_PROGRESS;
        ApplicationType applicationType = ApplicationType.INITIAL;
        String clientFirstName = "John";
        String clientLastName = "Doe";
        LocalDate clientDateOfBirth = LocalDate.of(1980, 5, 2);
        IndividualType clientType = IndividualType.CLIENT;

        // Builders use the data
        IndividualEntity individual = IndividualEntity.builder()
                .firstName(clientFirstName)
                .lastName(clientLastName)
                .dateOfBirth(clientDateOfBirth)
                .type(clientType)
                .build();

        CaseworkerEntity caseworker = CaseworkerEntity.builder()
                .id(caseworkerId)
                .build();

        ApplicationSummaryEntity entity = new ApplicationSummaryEntity();
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setModifiedAt(modifiedAt);
        entity.setSubmittedAt(submittedAt);
        entity.setAutoGranted(autoGranted);
        entity.setCategoryOfLaw(categoryOfLaw);
        entity.setMatterType(matterType);
        entity.setUsedDelegatedFunctions(usedDelegatedFunctions);
        entity.setLaaReference(laaReference);
        entity.setStatus(status);
        entity.setCaseworker(caseworker);
        entity.setIndividuals(Set.of(individual));
        entity.setType(applicationType);

        // Mapping
        ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

        // Asserts use the data
        assertThat(result).isNotNull();
        assertThat(result.getApplicationId()).isEqualTo(id);
        assertThat(result.getLastUpdated()).isEqualTo(modifiedAt.atOffset(ZoneOffset.UTC));
        assertThat(result.getSubmittedAt()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
        assertThat(result.getAutoGrant()).isEqualTo(autoGranted);
        assertThat(result.getCategoryOfLaw()).isEqualTo(categoryOfLaw);
        assertThat(result.getMatterType()).isEqualTo(matterType);
        assertThat(result.getUsedDelegatedFunctions()).isEqualTo(usedDelegatedFunctions);
        assertThat(result.getLaaReference()).isEqualTo(laaReference);
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(result.getAssignedTo()).isEqualTo(caseworkerId);
        assertThat(result.getClientFirstName()).isEqualTo(clientFirstName);
        assertThat(result.getClientLastName()).isEqualTo(clientLastName);
        assertThat(result.getClientDateOfBirth()).isEqualTo(clientDateOfBirth);
        assertThat(result.getApplicationType()).isEqualTo(applicationType);
    }

    @Test
    void givenNullApplicationSummary_whenToApplicationSummary_thenReturnNull() {
        assertThat(applicationMapper.toApplicationSummary(null)).isNull();
    }
}