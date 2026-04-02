package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest extends BaseMapperTest {

    @InjectMocks
    private ApplicationSummaryMapperImpl applicationMapper;

    private static List<Arguments> parametersForMappingApplicationSummaryEntityTest() {
        return List.of(
                Arguments.of(true),
                Arguments.of(false),
                Arguments.of((Boolean) null)
        );
    }

    @ParameterizedTest
    @MethodSource("parametersForMappingApplicationSummaryEntityTest")
    void givenApplicationSummaryResult_whenToApplicationSummary_thenMapsFieldsCorrectly(Boolean autoGranted) {
        UUID id = UUID.randomUUID();
        Instant modifiedAt = Instant.now();
        Instant submittedAt = Instant.now();
        CategoryOfLaw categoryOfLaw = CategoryOfLaw.FAMILY;
        MatterType matterType = MatterType.SPECIAL_CHILDREN_ACT;
        boolean usedDelegatedFunctions = true;
        String laaReference = "ref1";
        ApplicationStatus status = ApplicationStatus.APPLICATION_IN_PROGRESS;
        String officeCode = "office-code";

        ApplicationSummaryResult result = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .id(id)
                .modifiedAt(modifiedAt)
                .submittedAt(submittedAt)
                .isAutoGranted(autoGranted)
                .categoryOfLaw(categoryOfLaw)
                .matterType(matterType)
                .usedDelegatedFunctions(usedDelegatedFunctions)
                .laaReference(laaReference)
                .officeCode(officeCode)
                .status(status)
                .isLead(true));

        ApplicationSummary mapped = applicationMapper.toApplicationSummary(result);

        assertThat(mapped).isNotNull();
        assertThat(mapped.getApplicationId()).isEqualTo(id);
        assertThat(mapped.getLastUpdated()).isEqualTo(modifiedAt.atOffset(ZoneOffset.UTC));
        assertThat(mapped.getSubmittedAt()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
        assertThat(mapped.getAutoGrant()).isEqualTo(autoGranted);
        assertThat(mapped.getCategoryOfLaw()).isEqualTo(categoryOfLaw);
        assertThat(mapped.getMatterType()).isEqualTo(matterType);
        assertThat(mapped.getUsedDelegatedFunctions()).isEqualTo(usedDelegatedFunctions);
        assertThat(mapped.getLaaReference()).isEqualTo(laaReference);
        assertThat(mapped.getStatus()).isEqualTo(status);
        assertThat(mapped.getOfficeCode()).isEqualTo(officeCode);
        assertThat(mapped.getIsLead()).isTrue();
    }

    @Test
    void givenNullApplicationSummary_whenToApplicationSummary_thenReturnNull() {
        assertThat(applicationMapper.toApplicationSummary((ApplicationSummaryResult) null)).isNull();
    }

    @Test
    void givenApplicationSummaryResultWithNullSubmittedAt_whenToApplicationSummary_thenSubmittedAtIsNull() {
        ApplicationSummaryResult result = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .submittedAt(null));

        assertThat(applicationMapper.toApplicationSummary(result).getSubmittedAt()).isNull();
    }

    @Test
    void givenApplicationSummaryResultWithNullModifiedAt_whenToApplicationSummary_thenLastUpdatedIsNull() {
        ApplicationSummaryResult result = DataGenerator.createDefault(ApplicationSummaryGenerator.class, builder -> builder
                .modifiedAt(null));

        assertThat(applicationMapper.toApplicationSummary(result).getLastUpdated()).isNull();
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
