package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryDtoGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest extends BaseMapperTest {

  @InjectMocks private ApplicationSummaryMapperImpl applicationMapper;

  private static List<Arguments> parametersForMappingApplicationSummaryDtoTest() {
    return List.of(
        Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", true),
        Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", false),
        Arguments.of("95bb88f1-99ca-4ecf-b867-659b55a8cf93", null),
        Arguments.of(null, true),
        Arguments.of(null, false),
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("parametersForMappingApplicationSummaryDtoTest")
  void givenApplicationSummaryDto_whenToApplicationSummary_thenMapsFieldsCorrectly(
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

    var caseworker =
        DataGenerator.createDefault(CaseworkerGenerator.class, builder -> builder.id(caseworkerId));

    ApplicationSummaryDto summaryDto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class,
            builder ->
                builder
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
                    .clientFirstName("John")
                    .clientLastName("Doe")
                    .clientDateOfBirth(LocalDate.of(1980, 5, 2))
                    .caseworkerId(caseworker.getId()));

    ApplicationSummary result = applicationMapper.toApplicationSummary(summaryDto);

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
  void
      givenApplicationSummaryDtoWithNullSubmittedAt_whenToApplicationSummary_thenSubmittedAtIsNull() {
    ApplicationSummaryDto summaryDto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.submittedAt(null));

    assertThat(applicationMapper.toApplicationSummary(summaryDto).getSubmittedAt()).isNull();
  }

  @Test
  void
      givenApplicationSummaryDtoWithLinkedApplications_whenToApplicationSummary_thenIsLeadIsTrue() {

    ApplicationSummaryDto summaryDto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.isLead(true));

    assertThat(applicationMapper.toApplicationSummary(summaryDto).getIsLead()).isTrue();
  }

  @Test
  void
      givenApplicationSummaryDtoWithNoLinkedApplications_whenToApplicationSummary_thenIsLeadIsFalse() {
    ApplicationSummaryDto summaryDto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.isLead(false));

    assertThat(applicationMapper.toApplicationSummary(summaryDto).getIsLead()).isFalse();
  }

  @Test
  void
      givenApplicationSummaryDtoWithNoIndividuals_whenToApplicationSummary_thenClientFieldsAreNull() {
    ApplicationSummaryDto summaryDto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class,
            builder -> builder.clientLastName(null).clientFirstName(null).clientDateOfBirth(null));

    ApplicationSummary result = applicationMapper.toApplicationSummary(summaryDto);

    assertThat(result.getClientFirstName()).isNull();
    assertThat(result.getClientLastName()).isNull();
    assertThat(result.getClientDateOfBirth()).isNull();
  }

  @Test
  void givenLinkedApplicationsSummaryDto_whenToLinkedApplicationSummary_thenMapsFieldsCorrectly() {
    UUID applicationId = UUID.randomUUID();
    String laaReference = "ref1";
    boolean isLead = true;

    var expectedLinkSummary =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDtoGenerator.class,
            builder ->
                builder.applicationId(applicationId).laaReference(laaReference).isLead(isLead));

    LinkedApplicationSummaryResponse result =
        applicationMapper.toLinkedApplicationSummary(expectedLinkSummary);

    assertThat(result).isNotNull();
    assertThat(result.getApplicationId()).isEqualTo(applicationId);
    assertThat(result.getLaaReference()).isEqualTo(laaReference);
    assertThat(result.getIsLead()).isEqualTo(isLead);
  }
}
