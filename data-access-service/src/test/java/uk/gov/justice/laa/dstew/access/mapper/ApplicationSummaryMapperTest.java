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
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;


@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryMapperTest {

  @InjectMocks
  private ApplicationSummaryMapper applicationMapper = new ApplicationSummaryMapperImpl();

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
    // Test data setup
    UUID id = UUID.randomUUID();
    Instant modifiedAt = Instant.now();
    Instant submittedAt = Instant.now();
    CategoryOfLaw categoryOfLaw = CategoryOfLaw.FAMILY;
    MatterType matterType = MatterType.SCA;
    Boolean usedDelegatedFunctions = true;
    String laaReference = "ref1";
    ApplicationStatus status = ApplicationStatus.APPLICATION_IN_PROGRESS;
    ApplicationType applicationType = ApplicationType.INITIAL;
    String clientFirstName = "John";
    String clientLastName = "Doe";
    LocalDate clientDateOfBirth = LocalDate.of(1980, 5, 2);
    String officeCode = "office-code";

    ApplicationSummaryResult result = new ApplicationSummaryResult(
        id,
        laaReference,
        status,
        submittedAt,
        autoGranted,
        categoryOfLaw,
        matterType,
        usedDelegatedFunctions,
        officeCode,
        caseworkerId,
        clientFirstName,
        clientLastName,
        clientDateOfBirth,
        modifiedAt
    );

    // Mapping
    ApplicationSummary mapped = applicationMapper.toApplicationSummary(result);

    // Asserts use the data
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
    assertThat(mapped.getAssignedTo()).isEqualTo(caseworkerId);
    assertThat(mapped.getClientFirstName()).isEqualTo(clientFirstName);
    assertThat(mapped.getClientLastName()).isEqualTo(clientLastName);
    assertThat(mapped.getClientDateOfBirth()).isEqualTo(clientDateOfBirth);
    assertThat(mapped.getApplicationType()).isEqualTo(applicationType);
    assertThat(mapped.getOfficeCode()).isEqualTo(officeCode);
  }

  @Test
  void givenNullApplicationSummary_whenToApplicationSummary_thenReturnNull() {
    assertThat(applicationMapper.toApplicationSummary((ApplicationSummaryResult) null)).isNull();
  }
}