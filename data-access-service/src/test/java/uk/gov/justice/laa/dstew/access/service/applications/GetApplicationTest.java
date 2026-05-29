package uk.gov.justice.laa.dstew.access.service.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetApplicationTest extends BaseServiceTest {

  @Autowired private GetApplicationService serviceUnderTest;

  @Test
  public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
    // given
    MeritsDecisionEntity meritsDecision =
        DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class);

    UUID applyProceedingId = UUID.randomUUID();

    ProceedingEntity proceeding =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.meritsDecision(meritsDecision).applyProceedingId(applyProceedingId));
    Set<ProceedingEntity> proceedings = Set.of(proceeding);

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingMeritsGenerator.class,
                            meritsBuilder -> meritsBuilder.proceedingId(applyProceedingId)))));

    ApplicationEntity expectedApplication =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class,
            applicationEntityBuilder ->
                applicationEntityBuilder
                    .version(0L)
                    .proceedings(proceedings)
                    .applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    expectedApplication.setDecision(DataGenerator.createDefault(DecisionEntityGenerator.class));

    when(applicationRepository.findById(expectedApplication.getId()))
        .thenReturn(Optional.of(expectedApplication));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    ApplicationResponse actualApplication =
        serviceUnderTest.getApplication(expectedApplication.getId());

    // then
    assertApplicationEqual(expectedApplication, actualApplication);
    assertApplicationProceedingsEqual(
        proceedings, actualApplication.getProceedings(), MeritsDecisionStatus.REFUSED);

    ApplicationContent expectedApplicationContent =
        objectMapper.convertValue(
            expectedApplication.getApplicationContent(), ApplicationContent.class);
    InvolvedChildResponse actualApplicationInvolvedChild =
        actualApplication.getProceedings().getFirst().getInvolvedChildren().getFirst();
    assertThat(actualApplicationInvolvedChild.getFullName())
        .isEqualTo(
            expectedApplicationContent
                .getApplicationMerits()
                .getInvolvedChildren()
                .getFirst()
                .getFullName());
    assertThat(actualApplicationInvolvedChild.getDateOfBirth())
        .isEqualTo(
            expectedApplicationContent
                .getApplicationMerits()
                .getInvolvedChildren()
                .getFirst()
                .getDateOfBirth());

    verify(applicationRepository, times(1)).findById(expectedApplication.getId());
  }

  @Test
  public void
      givenApplicationWithInvolvedChildren_whenGetApplication_thenInvolvedChildrenResolvedInProceedings() {
    // given
    UUID applyProceedingId = UUID.randomUUID();

    // Build applicationContent with proceedingMerits linking applyProceedingId -> childId
    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingMeritsGenerator.class,
                            meritsBuilder -> meritsBuilder.proceedingId(applyProceedingId)))));

    ProceedingEntity proceeding =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationEntity application =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .version(0L)
                    .proceedings(Set.of(proceeding))
                    .applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    ApplicationResponse response = serviceUnderTest.getApplication(application.getId());

    // then
    assertThat(response.getProceedings()).isNotNull().hasSize(1);
    ApplicationProceedingResponse proceedingResponse = response.getProceedings().getFirst();
    assertThat(proceedingResponse.getInvolvedChildren()).isNotNull().hasSize(1);
    InvolvedChildResponse involvedChild = proceedingResponse.getInvolvedChildren().getFirst();
    ApplicationContent expectedApplicationContent =
        objectMapper.convertValue(application.getApplicationContent(), ApplicationContent.class);
    assertThat(involvedChild.getFullName())
        .isEqualTo(
            expectedApplicationContent
                .getApplicationMerits()
                .getInvolvedChildren()
                .getFirst()
                .getFullName());
    assertThat(involvedChild.getDateOfBirth())
        .isEqualTo(
            expectedApplicationContent
                .getApplicationMerits()
                .getInvolvedChildren()
                .getFirst()
                .getDateOfBirth());

    verify(applicationRepository, times(1)).findById(application.getId());
  }

  private Stream<Arguments> emptyInvolvedChildrenCases() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID nonExistentChildId = UUID.randomUUID();

    ApplicationContent withUnresolvableChild =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        DataGenerator.createDefault(
                            ProceedingMeritsGenerator.class,
                            meritsBuilder ->
                                meritsBuilder
                                    .proceedingId(applyProceedingId)
                                    .proceedingLinkedChildren(
                                        List.of(
                                            ProceedingLinkedChild.builder()
                                                .involvedChildId(nonExistentChildId)
                                                .build()))))));

    ApplicationContent withNoProceedingMerits =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.proceedingMerits(List.of()));

    return Stream.of(
        Arguments.of("unresolvable child ID", applyProceedingId, withUnresolvableChild),
        Arguments.of("no proceeding merits", UUID.randomUUID(), withNoProceedingMerits));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("emptyInvolvedChildrenCases")
  public void
      givenApplicationWithNoResolvableChildren_whenGetApplication_thenInvolvedChildrenIsEmpty(
          String description, UUID applyProceedingId, ApplicationContent applicationContent) {
    // given
    ProceedingEntity proceeding =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationEntity application =
        DataGenerator.createDefault(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .version(0L)
                    .proceedings(Set.of(proceeding))
                    .applicationContent(objectMapper.convertValue(applicationContent, Map.class)));

    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    ApplicationResponse response = serviceUnderTest.getApplication(application.getId());

    // then
    assertThat(response.getProceedings()).isNotNull().hasSize(1);
    assertThat(response.getProceedings().getFirst().getInvolvedChildren()).isEmpty();

    verify(applicationRepository, times(1)).findById(application.getId());
  }

  @Test
  public void
      givenNoApplicationAndRoleReader_whenGetApplication_thenThrowResourceNotFoundException() {

    // given
    UUID applicationId = UUID.randomUUID();
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when
    // then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> serviceUnderTest.getApplication(applicationId))
        .withMessageContaining("No application found with id: " + applicationId);
    verify(applicationRepository, times(1)).findById(applicationId);
  }

  @Test
  public void givenApplicationAndNotRoleReader_whenGetApplication_thenThrowUnauthorizedException() {

    // given
    UUID applicationId = UUID.randomUUID();

    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when
    // then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.getApplication(applicationId))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(applicationId);
  }

  @Test
  public void givenApplicationAndNoRole_whenGetApplication_thenThrowUnauthorizedException() {

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.getApplication(UUID.randomUUID()))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, times(0)).findById(any(UUID.class));
  }

  public void assertApplicationEqual(
      ApplicationEntity expectedApplication, ApplicationResponse actualApplication) {
    assertThat(actualApplication.getStatus()).isEqualTo(expectedApplication.getStatus());
    assertThat(actualApplication.getLaaReference())
        .isEqualTo(expectedApplication.getLaaReference());
    if (expectedApplication.getDecision() != null) {
      assertThat(actualApplication.getDecisionStatus())
          .isEqualTo(expectedApplication.getDecision().getOverallDecision());
    } else {
      assertThat(actualApplication.getDecisionStatus()).isNull();
    }
  }

  private void assertApplicationProceedingsEqual(
      Set<ProceedingEntity> expectedProceedings,
      List<ApplicationProceedingResponse> actualProceedings,
      MeritsDecisionStatus expectedStatus) {

    if (expectedProceedings == null && actualProceedings == null) {
      return;
    }

    assertThat(expectedProceedings).isNotNull();
    assertThat(actualProceedings).isNotNull();
    ProceedingEntity expectedProceedingEntity = expectedProceedings.iterator().next();
    ApplicationProceedingResponse actualApplicationProceedingResponse =
        actualProceedings.getFirst();

    assertThat(expectedProceedings.size()).isEqualTo(actualProceedings.size());
    assertThat(expectedProceedingEntity.getId())
        .isEqualTo(actualApplicationProceedingResponse.getProceedingId());
    assertThat(
            getValueFromProceedingContent(
                "meaning", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(actualApplicationProceedingResponse.getProceedingType());
    assertThat(expectedProceedingEntity.getDescription())
        .isEqualTo(actualApplicationProceedingResponse.getProceedingDescription());
    assertThat(
            getValueFromProceedingContent(
                "usedDelegatedFunctionsOn", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(actualApplicationProceedingResponse.getDelegatedFunctionsDate().toString());
    assertThat(
            getValueFromProceedingContent(
                "categoryOfLaw", expectedProceedingEntity.getProceedingContent()))
        .isEqualToIgnoringCase(actualApplicationProceedingResponse.getCategoryOfLaw().getValue());
    assertThat(
            getValueFromProceedingContent(
                "matterType", expectedProceedingEntity.getProceedingContent()))
        .isEqualToIgnoringCase(actualApplicationProceedingResponse.getMatterType().getValue());
    assertThat(
            getValueFromProceedingContent(
                "substantiveLevelOfServiceName", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(actualApplicationProceedingResponse.getLevelOfService());
    assertThat(
            getValueFromProceedingContent(
                "substantiveCostLimitation", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(actualApplicationProceedingResponse.getSubstantiveCostLimitation());
    assertThat(actualApplicationProceedingResponse.getScopeLimitations()).isNotNull();
    ScopeLimitationResponse scopeLimitation =
        actualApplicationProceedingResponse.getScopeLimitations().getFirst();
    assertThat(
            getValueFromScopeLimitations(
                0, "meaning", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(scopeLimitation.getScopeLimitation());
    assertThat(
            getValueFromScopeLimitations(
                0, "description", expectedProceedingEntity.getProceedingContent()))
        .isEqualTo(scopeLimitation.getScopeDescription());
    assertThat(expectedStatus).isEqualTo(actualApplicationProceedingResponse.getMeritsDecision());
  }

  private String getValueFromProceedingContent(
      String fieldName, Map<String, Object> proceedingContent) {
    return (String) proceedingContent.get(fieldName);
  }

  private String getValueFromScopeLimitations(
      int index, String fieldName, Map<String, Object> proceedingContent) {
    List<Map<String, Object>> scopeLimitations =
        (List<Map<String, Object>>) proceedingContent.get("scopeLimitations");
    Map<String, Object> scopeLimitation = scopeLimitations.get(index);
    return (String) scopeLimitation.get(fieldName);
  }
}
