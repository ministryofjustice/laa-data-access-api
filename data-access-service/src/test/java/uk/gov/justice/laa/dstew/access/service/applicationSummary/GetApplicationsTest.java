package uk.gov.justice.laa.dstew.access.service.applicationSummary;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryView;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GetApplicationsTest extends BaseServiceTest {

    @Autowired
    private ApplicationSummaryService serviceUnderTest;

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    public void givenPageZeroAndNoFilters_whenGetApplications_thenReturnApplications(int count) {
        // given
        List<ApplicationEntity> entities = DataGenerator.createMultipleRandom(ApplicationSummaryGenerator.class, count);
        List<ApplicationSummaryView> expectedApplications = entities.stream().map(this::toView).toList();
        Page<ApplicationSummaryView> pageResult = new PageImpl<>(expectedApplications);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "submittedAt"));

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(pageResult);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().stream().toList();

        // then
        verify(applicationRepository, times(1)).findBy(any(Specification.class), any(Function.class));
        assertApplicationSummaryListsEqual(actualApplications, expectedApplications);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    public void givenPageZeroAndCaseworkerFound_whenGetApplications_thenReturnApplications(int count) {
        // given
        UUID caseworkerId = UUID.randomUUID();
        when(caseworkerRepository.countById(caseworkerId)).thenReturn(1L);

        List<ApplicationEntity> entities = DataGenerator.createMultipleRandom(ApplicationSummaryGenerator.class, count);
        // ensure that at least one application has no caseworker assigned
        List<ApplicationSummaryView> expectedApplications = entities.stream().map(this::toView).toList();

        Page<ApplicationSummaryView> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(pageResult);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
                null, null, null, null, null, caseworkerId, null, null, null, null, 1, 10
        ).page().stream().toList();

        // then
        verify(applicationRepository, times(1)).findBy(any(Specification.class), any(Function.class));
        assertApplicationSummaryListsEqual(actualApplications, expectedApplications);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    public void givenPageZeroAndUserId_whenGetApplicationsAndNoCaseworkerFound_thenThrowValidationException() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);
        ValidationException validationException = new ValidationException(List.of("Caseworker not found"));

        // when / then
        Throwable thrown = catchThrowable(() -> serviceUnderTest.getAllApplications(
                null, null, null, null, null, UUID.randomUUID(), null, null, null, null, 1, 10
        ));
        assertThat(thrown)
                .isInstanceOf(ValidationException.class)
                .usingRecursiveComparison()
                .isEqualTo(validationException);

        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenNotRoleReader_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.NO_ROLE);

        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, null, null
                ))
                .withMessageContaining("Access Denied");
        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenNoRole_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, null, null
                ))
                .withMessageContaining("Access Denied");
        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenDefaultPagination_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryView> expectedApplications = DataGenerator.createMultipleRandom(ApplicationSummaryGenerator.class, 5)
                .stream().map(this::toView).toList();
        Page<ApplicationSummaryView> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        verify(applicationRepository, times(1)).findBy(any(Specification.class), any(Function.class));

        assertThat(result.requestedPage()).isEqualTo(1);
        assertThat(result.requestedPageSize()).isEqualTo(20);
        assertThat(result.page().getContent()).hasSize(5);
    }

    @Test
    public void givenSecondPage_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryView> expectedApplications = DataGenerator.createMultipleRandom(ApplicationSummaryGenerator.class, 5)
                .stream().map(this::toView).toList();
        Page<ApplicationSummaryView> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, 2, 10);

        // then
        verify(applicationRepository, times(1)).findBy(any(Specification.class), any(Function.class));

        assertThat(result.requestedPage()).isEqualTo(2);
        assertThat(result.requestedPageSize()).isEqualTo(10);
        assertThat(result.page().getContent()).hasSize(5);
    }

    @Test
    public void givenInvalidPage_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 0, 10))
                .withMessageContaining("page must be greater than or equal to 1");

        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenInvalidPageSize_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 1, 0))
                .withMessageContaining("pageSize must be greater than or equal to 1");

        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenPageSizeExceedingMax_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 1, 101))
                .withMessageContaining("pageSize cannot be more than 100");

        verify(applicationRepository, never()).findBy(any(Specification.class), any(Function.class));
    }

    @Test
    public void givenMaximumPageSize_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryView> expectedApplications = DataGenerator.createMultipleRandom(ApplicationSummaryGenerator.class, 5)
                .stream().map(this::toView).toList();
        Page<ApplicationSummaryView> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.CASEWORKER);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, 1, 100);

        // then
        verify(applicationRepository, times(1)).findBy(any(Specification.class), any(Function.class));

        assertThat(result.requestedPageSize()).isEqualTo(100);
        assertThat(result.page().getContent()).hasSize(5);
    }

    @Test
    public void givenOnlyLead_whenGetApplications_thenLinkedApplicationsContainsAssociateNotSelf() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        UUID associateId = UUID.randomUUID();
        ApplicationEntity leadEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class,
            b -> b.linkedApplications(Set.of(DataGenerator.createDefault(ApplicationEntityGenerator.class)))
        );
        ApplicationSummaryView leadView = toView(leadEntity);

        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(associateId, "ASSOC-REF-0", false, leadEntity.getId()),
            new LinkedApplicationSummaryDto(leadEntity.getId(), leadEntity.getLaaReference(), true, leadEntity.getId())
        );

        when(applicationRepository.findBy(any(Specification.class), any(Function.class)))
            .thenReturn(new PageImpl<>(List.of(leadView)));
        when(applicationRepository.findAllLeadIdsByPageIds(any())).thenReturn(List.of(leadEntity.getId()));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        assertThat(actualApplications).hasSize(1);
        List<LinkedApplicationSummaryResponse> linked = actualApplications.getFirst().getLinkedApplications();
        UUID leadId = actualApplications.getFirst().getApplicationId();

        assertThat(linked).hasSize(1);
        assertThat(linked.getFirst().getApplicationId()).isEqualTo(associateId);
        assertThat(linked.getFirst().getIsLead()).isFalse();
        assertThat(linked).noneMatch(la -> la.getApplicationId().equals(leadId));
    }

    @Test
    public void givenAssociatesOfSameLead_whenGetApplications_thenEachAssociateSeesLeadAndSiblingNotSelf() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        UUID leadId = UUID.randomUUID();
        ApplicationEntity firstEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class);
        ApplicationEntity secondEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class);
        ApplicationSummaryView firstAssociateView = toView(firstEntity);
        ApplicationSummaryView secondAssociateView = toView(secondEntity);

        List<LinkedApplicationSummaryDto> linkedApplicationSummaryDtos = List.of(
            DataGenerator.createDefault(LinkedApplicationSummaryDtoGenerator.class, b -> b.applicationId(firstEntity.getId()).laaReference(firstEntity.getLaaReference()).isLead(false).leadApplicationId(leadId)),
            DataGenerator.createDefault(LinkedApplicationSummaryDtoGenerator.class, b -> b.applicationId(secondEntity.getId()).laaReference(secondEntity.getLaaReference()).isLead(false).leadApplicationId(leadId)),
            DataGenerator.createDefault(LinkedApplicationSummaryDtoGenerator.class, b -> b.applicationId(leadId).laaReference("LEAD-REF").isLead(true).leadApplicationId(leadId))
        );

        when(applicationRepository.findBy(any(Specification.class), any(Function.class)))
            .thenReturn(new PageImpl<>(List.of(firstAssociateView, secondAssociateView)));
        when(applicationRepository.findAllLeadIdsByPageIds(any())).thenReturn(List.of(leadId));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedApplicationSummaryDtos);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();
        ApplicationSummary firstAssociate = actualApplications.get(0);
        ApplicationSummary secondAssociate = actualApplications.get(1);

        // then
        assertThat(firstAssociate.getLinkedApplications())
            .extracting(LinkedApplicationSummaryResponse::getApplicationId)
            .containsExactlyInAnyOrder(secondEntity.getId(), leadId);
        assertThat(firstAssociate.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(firstEntity.getId()));

        assertThat(secondAssociate.getLinkedApplications())
            .extracting(LinkedApplicationSummaryResponse::getApplicationId)
            .containsExactlyInAnyOrder(firstEntity.getId(), leadId);
        assertThat(secondAssociate.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(secondEntity.getId()));
    }

    @Test
    public void givenAssociatesOfDifferentLeads_whenGetApplications_thenNoGroupCrossContamination() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        UUID firstLeadId = UUID.randomUUID();
        UUID secondLeadId = UUID.randomUUID();
        ApplicationEntity firstEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class);
        ApplicationEntity secondEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class);
        ApplicationSummaryView firstAssociateView = toView(firstEntity);
        ApplicationSummaryView secondAssociateView = toView(secondEntity);

        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(firstEntity.getId(), firstEntity.getLaaReference(), false, firstLeadId),
            new LinkedApplicationSummaryDto(firstLeadId, "LEAD-REF-1", true, firstLeadId),
            new LinkedApplicationSummaryDto(secondEntity.getId(), secondEntity.getLaaReference(), false, secondLeadId),
            new LinkedApplicationSummaryDto(secondLeadId, "LEAD-REF-2", true, secondLeadId)
        );

        when(applicationRepository.findBy(any(Specification.class), any(Function.class)))
            .thenReturn(new PageImpl<>(List.of(firstAssociateView, secondAssociateView)));
        when(applicationRepository.findAllLeadIdsByPageIds(any())).thenReturn(List.of(firstLeadId, secondLeadId));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();
        ApplicationSummary firstAssociate = actualApplications.get(0);
        ApplicationSummary secondAssociate = actualApplications.get(1);

        // then
        assertThat(firstAssociate.getLinkedApplications())
            .extracting(LinkedApplicationSummaryResponse::getApplicationId)
            .containsExactlyInAnyOrder(firstLeadId);
        assertThat(firstAssociate.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(secondEntity.getId()));

        assertThat(secondAssociate.getLinkedApplications())
            .extracting(LinkedApplicationSummaryResponse::getApplicationId)
            .containsExactlyInAnyOrder(secondLeadId);
        assertThat(secondAssociate.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(firstEntity.getId()));
    }

    @Test
    public void givenOnlyStandalone_whenGetApplications_thenLinkedApplicationsIsEmpty() {
        // given
        setSecurityContext(TestConstants.Roles.CASEWORKER);

        ApplicationEntity standaloneEntity = DataGenerator.createDefault(ApplicationSummaryGenerator.class);
        ApplicationSummaryView standaloneView = toView(standaloneEntity);

        when(applicationRepository.findBy(any(Specification.class), any(Function.class)))
            .thenReturn(new PageImpl<>(List.of(standaloneView)));
        when(applicationRepository.findAllLeadIdsByPageIds(any())).thenReturn(List.of());

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        assertThat(actualApplications).hasSize(1);
        assertThat(actualApplications).allMatch(r -> r.getLinkedApplications().isEmpty());
        verify(applicationRepository, never()).findAllLinkedApplicationsByLeadIds(any());
    }

    private ApplicationSummaryView toView(ApplicationEntity entity) {
        ApplicationSummaryView view = mock(ApplicationSummaryView.class);
        when(view.getId()).thenReturn(entity.getId());
        when(view.getLaaReference()).thenReturn(entity.getLaaReference());
        when(view.getStatus()).thenReturn(entity.getStatus());
        when(view.getMatterType()).thenReturn(entity.getMatterType());
        when(view.getModifiedAt()).thenReturn(entity.getModifiedAt());
        if (entity.getCaseworker() != null) {
            ApplicationSummaryView.CaseworkerView caseworkerView = mock(ApplicationSummaryView.CaseworkerView.class);
            when(caseworkerView.getId()).thenReturn(entity.getCaseworker().getId());
            when(view.getCaseworker()).thenReturn(caseworkerView);
        } else {
            when(view.getCaseworker()).thenReturn(null);
        }
        return view;
    }

    private void assertApplicationSummaryListsEqual(List<ApplicationSummary> actualList, List<ApplicationSummaryView> expectedList) {
        assertThat(actualList).hasSameSizeAs(expectedList);

        for (ApplicationSummaryView expected : expectedList) {
            boolean match = actualList.stream()
                    .anyMatch(actual -> {
                        try {
                            assertApplicationSummaryEqual(expected, actual);
                            return true;
                        } catch (AssertionError e) {
                            return false;
                        }
                    });
            assertThat(match)
                    .as("No matching ApplicationSummaryView found for expected: " + expected)
                    .isTrue();
        }
    }

    private void assertApplicationSummaryEqual(ApplicationSummaryView expected, ApplicationSummary actual) {
        assertThat(expected.getId()).isEqualTo(actual.getApplicationId());
        assertThat(expected.getLaaReference()).isEqualTo(actual.getLaaReference());
        assertThat(expected.getStatus()).isEqualTo(actual.getStatus());
        assertThat(expected.getMatterType()).isEqualTo(actual.getMatterType());
        assertThat(expected.getModifiedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(actual.getLastUpdated().toInstant().truncatedTo(ChronoUnit.SECONDS));
        if (expected.getCaseworker() != null) {
            assertThat(expected.getCaseworker().getId()).isEqualTo(actual.getAssignedTo());
        }
    }
}
