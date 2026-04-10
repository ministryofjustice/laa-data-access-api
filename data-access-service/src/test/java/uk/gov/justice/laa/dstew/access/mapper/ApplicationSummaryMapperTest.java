package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryView;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;

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
    void givenApplicationSummaryView_whenToApplicationSummary_thenMapsFieldsCorrectly(
            UUID caseworkerId, Boolean autoGranted) {
        UUID id = UUID.randomUUID();
        Instant modifiedAt = Instant.now();
        Instant submittedAt = Instant.now();
        CategoryOfLaw categoryOfLaw = CategoryOfLaw.FAMILY;
        MatterType matterType = MatterType.SPECIAL_CHILDREN_ACT;
        boolean usedDelegatedFunctions = true;
        String laaReference = "ref1";
        ApplicationStatus status = ApplicationStatus.APPLICATION_IN_PROGRESS;
        String officeCode = "office-code";

        ApplicationSummaryView.CaseworkerView caseworkerView = null;
        if (caseworkerId != null) {
            caseworkerView = mock(ApplicationSummaryView.CaseworkerView.class);
            when(caseworkerView.getId()).thenReturn(caseworkerId);
        }

        ApplicationSummaryView view = mock(ApplicationSummaryView.class);
        when(view.getId()).thenReturn(id);
        when(view.getModifiedAt()).thenReturn(modifiedAt);
        when(view.getSubmittedAt()).thenReturn(submittedAt);
        when(view.getIsAutoGranted()).thenReturn(autoGranted);
        when(view.getCategoryOfLaw()).thenReturn(categoryOfLaw);
        when(view.getMatterType()).thenReturn(matterType);
        when(view.getUsedDelegatedFunctions()).thenReturn(usedDelegatedFunctions);
        when(view.getLaaReference()).thenReturn(laaReference);
        when(view.getOfficeCode()).thenReturn(officeCode);
        when(view.getStatus()).thenReturn(status);
        when(view.getCaseworker()).thenReturn(caseworkerView);
        when(view.getIndividualsFirstName()).thenReturn("John");
        when(view.getIndividualsLastName()).thenReturn("Doe");
        when(view.getIndividualsDateOfBirth()).thenReturn(LocalDate.of(1980, 5, 2));

        ApplicationSummary result = applicationMapper.toApplicationSummary(view);

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
        assertThat(result.getApplicationType()).isEqualTo(ApplicationType.INITIAL);
        assertThat(result.getOfficeCode()).isEqualTo(officeCode);
    }

    @Test
    void givenNullApplicationSummary_whenToApplicationSummary_thenReturnNull() {
        assertThat(applicationMapper.toApplicationSummary(null)).isNull();
    }

    @Test
    void givenApplicationSummaryViewWithNullSubmittedAt_whenToApplicationSummary_thenSubmittedAtIsNull() {
        ApplicationSummaryView view = mock(ApplicationSummaryView.class);
        when(view.getSubmittedAt()).thenReturn(null);
        when(view.getModifiedAt()).thenReturn(Instant.now());
        when(view.getIndividualsFirstName()).thenReturn(null);
        when(view.getIndividualsLastName()).thenReturn(null);
        when(view.getIndividualsDateOfBirth()).thenReturn(null);

        assertThat(applicationMapper.toApplicationSummary(view).getSubmittedAt()).isNull();
    }

    @Test
    void givenApplicationSummaryViewWithNoIndividuals_whenToApplicationSummary_thenClientFieldsAreNull() {
        ApplicationSummaryView view = mock(ApplicationSummaryView.class);
        when(view.getModifiedAt()).thenReturn(Instant.now());
        when(view.getIndividualsFirstName()).thenReturn(null);
        when(view.getIndividualsLastName()).thenReturn(null);
        when(view.getIndividualsDateOfBirth()).thenReturn(null);

        ApplicationSummary result = applicationMapper.toApplicationSummary(view);

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
