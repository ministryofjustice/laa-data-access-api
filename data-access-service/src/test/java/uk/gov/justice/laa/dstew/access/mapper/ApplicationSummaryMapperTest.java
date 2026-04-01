package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest extends BaseMapperTest {

    @InjectMocks
    private ApplicationSummaryMapperImpl applicationMapper;

    private static List<Arguments> parametersForMappingApplicationSummaryEntityTest() {
        return List.of(
                Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", true),
                Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", false),
                Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", null),
                Arguments.of(null, true),
                Arguments.of(null, false),
                Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("parametersForMappingApplicationSummaryEntityTest")
    void givenApplicationSummaryEntity_whenToApplicationSummary_thenMapsFieldsCorrectly(
            UUID caseworkerId, Boolean autoGranted) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant modifiedAt = Instant.now();
        Instant submittedAt = Instant.now();
        CategoryOfLaw categoryOfLaw = CategoryOfLaw.FAMILY;
        MatterType matterType = MatterType.SPECIAL_CHILDREN_ACT;
        boolean usedDelegatedFunctions = true;
        String laaReference = "ref1";
        ApplicationStatus status = ApplicationStatus.APPLICATION_IN_PROGRESS;
        ApplicationType applicationType = ApplicationType.INITIAL;
        String officeCode = "office-code";

        var individual = DataGenerator.createDefault(IndividualEntityGenerator.class, builder -> builder
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 5, 2))
                .type(IndividualType.CLIENT));

        var caseworker = DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder
                .id(caseworkerId));

        ApplicationSummaryEntity entity = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .id(id)
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .submittedAt(submittedAt)
                .isAutoGranted(autoGranted)
                .categoryOfLaw(categoryOfLaw)
                .matterType(matterType)
                .usedDelegatedFunctions(usedDelegatedFunctions)
                .laaReference(laaReference)
                .officeCode(officeCode)
                .status(status)
                .caseworker(caseworker)
                .individuals(Set.of(individual))
                .type(applicationType));

        ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

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
        assertThat(result.getClientFirstName()).isEqualTo("John");
        assertThat(result.getClientLastName()).isEqualTo("Doe");
        assertThat(result.getClientDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 2));
        assertThat(result.getApplicationType()).isEqualTo(applicationType);
        assertThat(result.getOfficeCode()).isEqualTo(officeCode);
    }

    @Test
    void givenNullApplicationSummary_whenToApplicationSummary_thenReturnNull() {
        assertThat(applicationMapper.toApplicationSummary(null)).isNull();
    }

    @Test
    void givenApplicationSummaryEntityWithNullSubmittedAt_whenToApplicationSummary_thenSubmittedAtIsNull() {
        ApplicationSummaryEntity entity = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .submittedAt(null)
                .individuals(Set.of()));

        assertThat(applicationMapper.toApplicationSummary(entity).getSubmittedAt()).isNull();
    }

    @Test
    void givenApplicationSummaryEntityWithLinkedApplications_whenToApplicationSummary_thenIsLeadIsTrue() {
        var linkedApplication = DataGenerator.createDefault(ApplicationEntityGenerator.class);

        ApplicationSummaryEntity entity = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .individuals(Set.of())
                .linkedApplications(Set.of(linkedApplication)));

        assertThat(applicationMapper.toApplicationSummary(entity).getIsLead()).isTrue();
    }

    @Test
    void givenApplicationSummaryEntityWithNoLinkedApplications_whenToApplicationSummary_thenIsLeadIsFalse() {
        ApplicationSummaryEntity entity = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .individuals(Set.of())
                .linkedApplications(null));

        assertThat(applicationMapper.toApplicationSummary(entity).getIsLead()).isFalse();
    }

    @Test
    void givenApplicationSummaryEntityWithNoIndividuals_whenToApplicationSummary_thenClientFieldsAreNull() {
        ApplicationSummaryEntity entity = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .individuals(Set.of()));

        ApplicationSummary result = applicationMapper.toApplicationSummary(entity);

        assertThat(result.getClientFirstName()).isNull();
        assertThat(result.getClientLastName()).isNull();
        assertThat(result.getClientDateOfBirth()).isNull();
    }

    @Test
    void givenLinkedApplicationsSummaryDto_whenToLinkedApplicationSummary_thenMapsFieldsCorrectly() {
        UUID applicationId = UUID.randomUUID();
        String laaReference = "ref1";
        boolean isLead = true;

        var expectedLinkSummary = DataGenerator.createDefault(LinkedApplicationSummaryDtoGenerator.class, builder -> builder
                .applicationId(applicationId)
                .laaReference(laaReference)
                .isLead(isLead));

        LinkedApplicationSummaryResponse result = applicationMapper.toLinkedApplicationSummary(expectedLinkSummary);

        assertThat(result).isNotNull();
        assertThat(result.getApplicationId()).isEqualTo(applicationId);
        assertThat(result.getLaaReference()).isEqualTo(laaReference);
        assertThat(result.getIsLead()).isEqualTo(isLead);
    }
}

