package uk.gov.justice.laa.dstew.access.service.application;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

public class GetApplicationTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Test
    public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
        // given
        ProceedingEntity proceeding = DataGenerator.createDefault(ProceedingsEntityGenerator.class);
        Set<ProceedingEntity> proceedings = Set.of(proceeding);

        ApplicationEntity expectedApplication = DataGenerator
            .createDefault(ApplicationEntityGenerator.class, applicationEntityBuilder ->
            applicationEntityBuilder.version(0L));

        expectedApplication.setDecision(DataGenerator.createDefault(DecisionEntityGenerator.class));
        expectedApplication.getDecision().setMeritsDecisions(
                Set.of(DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class,
                        builder -> builder.proceeding(proceeding))));

        when(proceedingRepository.findAllByApplicationId(expectedApplication.getId())).thenReturn(proceedings);
        when(applicationRepository.findByIdWithAssociations(expectedApplication.getId())).thenReturn(Optional.of(expectedApplication));

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when
        ApplicationResponse actualApplication = serviceUnderTest.getApplication(expectedApplication.getId());

        // then
        assertApplicationEqual(expectedApplication, actualApplication);
        assertApplicationProceedingsEqual(proceedings,
                                            actualApplication.getProceedings(),
                                            MeritsDecisionStatus.REFUSED);
        // assertThat(actualApplication.getProceedings().getFirst().getInvolvedChildren()).hasSize(1);

        // Map<String, Object> actualApplicationInvolvedChild =
        //        objectMapper.convertValue(actualApplication.getProceedings().getFirst().getInvolvedChildren().getFirst(), Map.class);
        ApplicationContent expectedApplicationContent =
                objectMapper.convertValue(expectedApplication.getApplicationContent(), ApplicationContent.class);

        // assertThat(expectedApplicationContent.getApplicationMerits().getInvolvedChildren().getFirst())
        //        .isEqualTo(actualApplicationInvolvedChild);
        verify(applicationRepository, times(1)).findByIdWithAssociations(expectedApplication.getId());
    }

    @Test
    public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowResourceNotFoundException() {

        // given
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findByIdWithAssociations(applicationId)).thenReturn(Optional.empty());

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when
        // then
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> serviceUnderTest.getApplication(applicationId))
                .withMessageContaining("No application found with id: " + applicationId);
        verify(applicationRepository, times(1)).findByIdWithAssociations(applicationId);
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

    public void assertApplicationEqual(ApplicationEntity expectedApplication, ApplicationResponse actualApplication) {
        assertThat(actualApplication.getStatus()).isEqualTo(expectedApplication.getStatus());
        assertThat(actualApplication.getLaaReference()).isEqualTo(expectedApplication.getLaaReference());
    }

    private void assertApplicationProceedingsEqual(Set<ProceedingEntity> expectedProceedings,
                                        List<ApplicationProceedingResponse> actualProceedings,
                                        MeritsDecisionStatus expectedStatus) {

        if (expectedProceedings == null && actualProceedings == null) {
            return;
        }

        assertThat(expectedProceedings).isNotNull();
        assertThat(actualProceedings).isNotNull();
        ProceedingEntity expectedProceedingEntity = expectedProceedings.iterator().next();
        ApplicationProceedingResponse actualApplicationProceedingResponse = actualProceedings.getFirst();

        assertThat(expectedProceedings.size()).isEqualTo(actualProceedings.size());
        assertThat(expectedProceedingEntity.getId()).isEqualTo(actualApplicationProceedingResponse.getProceedingId());
        assertThat(getValueFromProceedingContent("meaning", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceedingResponse.getProceedingType());
        assertThat(expectedProceedingEntity.getDescription()).isEqualTo(actualApplicationProceedingResponse.getProceedingDescription());
        assertThat(getValueFromProceedingContent("usedDelegatedFunctionsOn", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceedingResponse.getDelegatedFunctionsDate().toString());
        assertThat(getValueFromProceedingContent("categoryOfLaw", expectedProceedingEntity.getProceedingContent()))
                .isEqualToIgnoringCase(actualApplicationProceedingResponse.getCategoryOfLaw().getValue());
        assertThat(getValueFromProceedingContent("matterType", expectedProceedingEntity.getProceedingContent()))
                .isEqualToIgnoringCase(actualApplicationProceedingResponse.getMatterType().getValue());
        assertThat(getValueFromProceedingContent("substantiveLevelOfServiceName", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceedingResponse.getLevelOfService());
        assertThat(getValueFromProceedingContent("substantiveCostLimitation", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceedingResponse.getSubstantiveCostLimitation());
        assertThat(actualApplicationProceedingResponse.getScopeLimitations()).isNotNull();
        ScopeLimitationResponse scopeLimitation = actualApplicationProceedingResponse.getScopeLimitations().getFirst();
        assertThat(getValueFromScopeLimitations(0, "meaning", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(scopeLimitation.getScopeLimitation());
        assertThat(getValueFromScopeLimitations(0, "description", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(scopeLimitation.getScopeDescription());
        assertThat(expectedStatus).isEqualTo(actualApplicationProceedingResponse.getMeritsDecision());
    }

    private String getValueFromProceedingContent(String fieldName, Map<String, Object> proceedingContent) {
        return (String) proceedingContent.get(fieldName);
    }

    private String getValueFromScopeLimitations(int index, String fieldName, Map<String, Object> proceedingContent) {
        List<Map<String, Object>> scopeLimitations = (List<Map<String, Object>>) proceedingContent.get("scopeLimitations");
        Map<String, Object> scopeLimitation = scopeLimitations.get(index);
        return (String) scopeLimitation.get(fieldName);
    }
}
