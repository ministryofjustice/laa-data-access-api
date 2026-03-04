package uk.gov.justice.laa.dstew.access.service.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.helpers.SpringContext;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GetApplicationTest extends BaseServiceTest {

    @Autowired
    private ApplicationService serviceUnderTest;

    @Test
    public void givenApplicationEntityAndRoleReader_whenGetApplication_thenReturnMappedApplication() {
        // given
        ProceedingEntity proceeding =
                DataGenerator.createDefault(
                        ProceedingsEntityGenerator.class,
                        builder -> builder.id(UUID.randomUUID())
                                                                .proceedingContent(Map.of(
                                                                    "meaning", "mean",
                                                                        "matterType", "SCA",
                                                                        "categoryOfLaw", "FAMILY",
                                                                        "usedDelegatedFunctionsOn", "2025-05-06",
                                                                        "substantiveCostLimitation", "3.56",
                                                                        "substantiveLevelOfServiceName", "myService",
                                                                        "scopeLimitations", List.of(
                                                                                Map.of(
                                                                                "id", "100",
                                                                                "code", "AB123D",
                                                                                "meaning", "hearing"
                                                                                )
                                                                            )
                                                                        )
                                                                        ));
        ApplicationEntity expectedApplication = DataGenerator.createDefault(ApplicationEntityGenerator.class);
        expectedApplication.setDecision(DataGenerator.createDefault(DecisionEntityGenerator.class));
        expectedApplication.getDecision().setMeritsDecisions(
                Set.of(DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class,
                        builder -> builder.proceeding(proceeding))));

        Map<String, Object> applicationContent = expectedApplication.getApplicationContent();
        applicationContent.put("applicationMerits",
                Map.of("involvedChildren",
                    List.of(
                        Map.of("first_name", "John",
                                "last_name", "Smith",
                                "date_of_birth", "Mon Aug 20 2022 20:20:00 GMT+0100 (British Summer Time)")
                    )
                ));

        expectedApplication.setProceedings(Set.of(proceeding));
        when(applicationRepository.findById(expectedApplication.getId())).thenReturn(Optional.of(expectedApplication));

        setSecurityContext(TestConstants.Roles.READER);

        // when
        Application actualApplication = serviceUnderTest.getApplication(expectedApplication.getId());

        // then
        assertApplicationEqual(expectedApplication, actualApplication);
        assertApplicationProceedingsEqual(expectedApplication.getProceedings(),
                                            actualApplication.getProceedings(),
                MeritsDecisionStatus.REFUSED);
        assertThat(actualApplication.getProceedings().getFirst().getInvolvedChildren()).hasSize(1);
        Map<String, Object> data = (Map<String, Object>) actualApplication.getProceedings().getFirst().getInvolvedChildren().getFirst();
        assertThat(data.get("first_name")).isEqualTo("John");
        assertThat(data.get("last_name")).isEqualTo("Smith");
        assertThat(data.get("date_of_birth")).isEqualTo("Mon Aug 20 2022 20:20:00 GMT+0100 (British Summer Time)");
        verify(applicationRepository, times(1)).findById(expectedApplication.getId());
    }

    @Test
    public void givenNoApplicationAndRoleReader_whenGetApplication_thenThrowResourceNotFoundException() {

        // given
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        setSecurityContext(TestConstants.Roles.READER);

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

    public void assertApplicationEqual(ApplicationEntity expectedApplication, Application actualApplication) {
        assertThat(actualApplication.getStatus()).isEqualTo(expectedApplication.getStatus());
        assertThat(actualApplication.getLaaReference()).isEqualTo(expectedApplication.getLaaReference());
    }

    private void assertApplicationProceedingsEqual(Set<ProceedingEntity> expectedProceedings,
                                        List<ApplicationProceeding> actualProceedings,
                                        MeritsDecisionStatus expectedStatus) {

        if (expectedProceedings == null && actualProceedings == null) {
            return;
        }

        assertThat(expectedProceedings).isNotNull();
        assertThat(actualProceedings).isNotNull();
        ProceedingEntity expectedProceedingEntity = expectedProceedings.iterator().next();
        ApplicationProceeding actualApplicationProceeding = actualProceedings.getFirst();

        assertThat(expectedProceedings.size()).isEqualTo(actualProceedings.size());
        assertThat(expectedProceedingEntity.getId()).isEqualTo(actualApplicationProceeding.getProceedingId());
        assertThat(getValueFromProceedingContent("meaning", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getProceedingType());
        assertThat(expectedProceedingEntity.getDescription()).isEqualTo(actualApplicationProceeding.getProceedingDescription());
        assertThat(getValueFromProceedingContent("usedDelegatedFunctionsOn", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getUsedDelegatedFunctionsOn().toString());
        assertThat(getValueFromProceedingContent("categoryOfLaw", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getCategoryOfLaw());
        assertThat(getValueFromProceedingContent("matterType", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getMatterType());
        assertThat(getValueFromProceedingContent("substantiveLevelOfServiceName", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getLevelOfService());
        assertThat(getValueFromProceedingContent("substantiveCostLimitation", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(actualApplicationProceeding.getSubstantiveCostLimitation());
        List<Map<String, Object>> scopeLimitations = (List<Map<String, Object>>) actualApplicationProceeding.getScopeLimitations().getFirst();
        assertThat(scopeLimitations).isNotNull();
        Map<String, Object> scopeLimitation = scopeLimitations.getFirst();
        assertThat(getValueFromScopeLimitations(0, "id", expectedProceedingEntity.getProceedingContent()))
                    .isEqualTo(scopeLimitation.get("id").toString());
        assertThat(getValueFromScopeLimitations(0, "code", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(scopeLimitation.get("code").toString());
        assertThat(getValueFromScopeLimitations(0, "meaning", expectedProceedingEntity.getProceedingContent()))
                .isEqualTo(scopeLimitation.get("meaning").toString());
        assertThat(expectedStatus).isEqualTo(actualApplicationProceeding.getMeritsDecision());
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
