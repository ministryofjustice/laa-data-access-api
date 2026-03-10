package uk.gov.justice.laa.dstew.access.service.applicationSummary;

import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(count);
        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "submittedAt"));

        setSecurityContext(TestConstants.Roles.READER);

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                10
        ).page().stream().toList();

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), eq(pageable));
        assertApplicationSummaryListsEqual(actualApplications, expectedApplications);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    public void givenPageZeroAndCaseworkerFound_whenGetApplications_thenReturnApplications(int count) {
        // given

        UUID caseworkerId = UUID.randomUUID();
        when(caseworkerRepository.countById(caseworkerId)).thenReturn(1L);

        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(count);
        // ensure that at least one application has no caseworker assigned
        if (count > 0) { expectedApplications.getFirst().setCaseworker(null); }

        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "submittedAt"));

        setSecurityContext(TestConstants.Roles.READER);

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        // when
        List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
                null,
                null,
                null,
                null,
                null,
                caseworkerId,
                null,
                null,
                null,
                null,
                1,
                10
        ).page().stream().toList();

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), eq(pageable));
        assertApplicationSummaryListsEqual(actualApplications, expectedApplications);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    public void givenPageZeroAndUserId_whenGetApplicationsAndNoCaseworkerFound_thenThrowValidationException() {

        // given
        setSecurityContext(TestConstants.Roles.READER);
        ValidationException validationException = new ValidationException(List.of(
                "Caseworker not found"
        ));

        // when
        // then
        Throwable thrown = catchThrowable(() -> serviceUnderTest.getAllApplications(
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                1,
                10
        ));
        assertThat(thrown)
                .isInstanceOf(ValidationException.class)
                .usingRecursiveComparison()
                .isEqualTo(validationException);

        verify(applicationSummaryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenNotRoleReader_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.NO_ROLE);

        // when
        // then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
                .withMessageContaining("Access Denied");
        verify(applicationSummaryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenNoRole_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
        // given
        // when
        // then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
                .withMessageContaining("Access Denied");
        verify(applicationSummaryRepository, never()).findAll();
    }

    @Test
    public void givenDefaultPagination_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(5);
        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.READER);

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        assertThat(result.requestedPage()).isEqualTo(1); // one-based
        assertThat(result.requestedPageSize()).isEqualTo(20); // default
        assertThat(result.page().getContent()).hasSize(5);
    }

    @Test
    public void givenSecondPage_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(5);
        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.READER);

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, 2, 10);

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        assertThat(result.requestedPage()).isEqualTo(2); // one-based
        assertThat(result.requestedPageSize()).isEqualTo(10);
        assertThat(result.page().getContent()).hasSize(5);
    }

    @Test
    public void givenInvalidPage_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.READER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 0, 10))
                .withMessageContaining("page must be greater than or equal to 1");

        verify(applicationSummaryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenInvalidPageSize_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.READER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 1, 0))
                .withMessageContaining("pageSize must be greater than or equal to 1");

        verify(applicationSummaryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenPageSizeExceedingMax_whenGetApplications_thenThrowException() {
        // given
        setSecurityContext(TestConstants.Roles.READER);

        // when/then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serviceUnderTest.getAllApplications(
                        null, null, null, null, null, null, null, null, null, null, 1, 101))
                .withMessageContaining("pageSize cannot be more than 100");

        verify(applicationSummaryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    public void givenMaximumPageSize_whenGetApplications_thenReturnApplications() {
        // given
        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(5);
        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);

        setSecurityContext(TestConstants.Roles.READER);

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        // when
        PaginatedResult<ApplicationSummary> result = serviceUnderTest.getAllApplications(
                null, null, null, null, null, null, null, null, null, null, 1, 100);

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        assertThat(result.requestedPageSize()).isEqualTo(100);
        assertThat(result.page().getContent()).hasSize(5);
    }

    private void assertApplicationSummaryListsEqual(List<ApplicationSummary> actualList, List<ApplicationSummaryEntity> expectedList) {
        assertThat(actualList).hasSameSizeAs(expectedList);

        for (ApplicationSummaryEntity expected : expectedList) {
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
                    .as("No matching ApplicationSummaryEntity found for expected: " + expected)
                    .isTrue();
        }
    }

    private void assertApplicationSummaryEqual(ApplicationSummaryEntity expected, ApplicationSummary actual) {
        assertThat(expected.getId()).isEqualTo(actual.getApplicationId());
        assertThat(expected.getLaaReference()).isEqualTo(actual.getLaaReference());
        assertThat(expected.getStatus()).isEqualTo(actual.getStatus());
        assertThat(expected.getMatterType()).isEqualTo(actual.getMatterType());
        assertThat(expected.getModifiedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(actual.getLastUpdated().toInstant().truncatedTo(ChronoUnit.SECONDS));
        if (expected.getCaseworker() != null) {
            assertThat(expected.getCaseworker().getId()).isEqualTo(actual.getAssignedTo());
        }
    }

    @Test
    public void givenOnlyLeads_whenGetApplications_thenLinkedApplicationsContainsAssociatesNotSelf() {
        setSecurityContext(TestConstants.Roles.READER);

        UUID associateId = UUID.randomUUID();
        ApplicationSummaryEntity lead = applicationSummaryEntityFactory.createDefault(
            b -> b.linkedApplications(Set.of(applicationEntityFactory.createDefault()))
        );

        List<ApplicationSummaryEntity> leads = List.of(lead);
        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(associateId, "ASSOC-REF-0", false, lead.getId()),
            new LinkedApplicationSummaryDto(lead.getId(), lead.getLaaReference(), true, lead.getId())
        );

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(leads));
        when(applicationRepository.findLeadIdsByAssociatedIds(any())).thenReturn(List.of());
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> results = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        assertThat(results).hasSize(1);
        List<uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary> linked = results.getFirst().getLinkedApplications();
        UUID leadId = results.getFirst().getApplicationId();

        assertThat(linked).hasSize(1);
        assertThat(linked.getFirst().getApplicationId()).isEqualTo(associateId);
        assertThat(linked.getFirst().getIsLead()).isFalse();
        assertThat(linked).noneMatch(la -> {
            Assertions.assertNotNull(la.getApplicationId());
            return la.getApplicationId().equals(leadId);
        });
    }

    @Test
    public void givenAssociatesOfSameLead_whenGetApplications_thenEachAssociateSeesLeadAndSiblingNotSelf() {
        setSecurityContext(TestConstants.Roles.READER);

        UUID leadId = UUID.randomUUID();
        ApplicationSummaryEntity associate1 = applicationSummaryEntityFactory.createDefault();
        ApplicationSummaryEntity associate2 = applicationSummaryEntityFactory.createDefault();

        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(associate1.getId(), associate1.getLaaReference(), false, leadId),
            new LinkedApplicationSummaryDto(associate2.getId(), associate2.getLaaReference(), false, leadId),
            new LinkedApplicationSummaryDto(leadId, "LEAD-REF", true, leadId)
        );

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(associate1, associate2)));
        when(applicationRepository.findLeadIdsByAssociatedIds(any())).thenReturn(List.of(leadId));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> results = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        ApplicationSummary result1 = results.get(0);
        ApplicationSummary result2 = results.get(1);

        assertThat(result1.getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(associate2.getId(), leadId);
        assertThat(result1.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(associate1.getId()));

        assertThat(result2.getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(associate1.getId(), leadId);
        assertThat(result2.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(associate2.getId()));
    }

    @Test
    public void givenAssociatesOfDifferentLeads_whenGetApplications_thenNoGroupCrossContamination() {
        setSecurityContext(TestConstants.Roles.READER);

        UUID leadId1 = UUID.randomUUID();
        UUID leadId2 = UUID.randomUUID();
        ApplicationSummaryEntity associate1 = applicationSummaryEntityFactory.createDefault();
        ApplicationSummaryEntity associate2 = applicationSummaryEntityFactory.createDefault();

        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(associate1.getId(), associate1.getLaaReference(), false, leadId1),
            new LinkedApplicationSummaryDto(leadId1, "LEAD-REF-1", true, leadId1),
            new LinkedApplicationSummaryDto(associate2.getId(), associate2.getLaaReference(), false, leadId2),
            new LinkedApplicationSummaryDto(leadId2, "LEAD-REF-2", true, leadId2)
        );

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(associate1, associate2)));
        when(applicationRepository.findLeadIdsByAssociatedIds(any())).thenReturn(List.of(leadId1, leadId2));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> results = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        assertThat(results.get(0).getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(leadId1);
        assertThat(results.get(0).getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(associate2.getId()));

        assertThat(results.get(1).getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(leadId2);
        assertThat(results.get(1).getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(associate1.getId()));
    }

    @Test
    public void givenMixedPage_whenGetApplications_thenLinkedApplicationsCorrectForEachType() {
        setSecurityContext(TestConstants.Roles.READER);

        UUID offPageAssociateId = UUID.randomUUID();

        ApplicationSummaryEntity lead = applicationSummaryEntityFactory.createDefault(
            b -> b.linkedApplications(Set.of(applicationEntityFactory.createDefault()))
        );
        ApplicationSummaryEntity associate = applicationSummaryEntityFactory.createDefault();
        ApplicationSummaryEntity standalone = applicationSummaryEntityFactory.createDefault();

        List<LinkedApplicationSummaryDto> linkedDtos = List.of(
            new LinkedApplicationSummaryDto(associate.getId(), associate.getLaaReference(), false, lead.getId()),
            new LinkedApplicationSummaryDto(offPageAssociateId, "OFF-PAGE-REF", false, lead.getId()),
            new LinkedApplicationSummaryDto(lead.getId(), lead.getLaaReference(), true, lead.getId())
        );

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(lead, associate, standalone)));
        when(applicationRepository.findLeadIdsByAssociatedIds(any())).thenReturn(List.of(lead.getId()));
        when(applicationRepository.findAllLinkedApplicationsByLeadIds(any())).thenReturn(linkedDtos);

        // when
        List<ApplicationSummary> results = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        ApplicationSummary leadResult = results.get(0);
        ApplicationSummary associateResult = results.get(1);
        ApplicationSummary standaloneResult = results.get(2);

        // lead sees on-page and off-page associates, not itself
        assertThat(leadResult.getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(associate.getId(), offPageAssociateId);
        assertThat(leadResult.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(lead.getId()));

        // associate sees lead and off-page sibling, not itself
        assertThat(associateResult.getLinkedApplications())
            .extracting(uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummary::getApplicationId)
            .containsExactlyInAnyOrder(lead.getId(), offPageAssociateId);
        assertThat(associateResult.getLinkedApplications())
            .noneMatch(la -> la.getApplicationId().equals(associate.getId()));

        // standalone has no linked applications
        assertThat(standaloneResult.getLinkedApplications()).isEmpty();
    }

    @Test
    public void givenOnlyStandalone_whenGetApplications_thenLinkedApplicationsIsEmpty() {
        setSecurityContext(TestConstants.Roles.READER);

        ApplicationSummaryEntity standalone = applicationSummaryEntityFactory.createDefault();

        when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(standalone)));
        when(applicationRepository.findLeadIdsByAssociatedIds(any())).thenReturn(List.of());

        // when
        List<ApplicationSummary> results = serviceUnderTest.getAllApplications(
            null, null, null, null, null, null, null, null, null, null, 1, 10
        ).page().getContent();

        // then
        assertThat(results).hasSize(1);
        assertThat(results).allMatch(r -> r.getLinkedApplications().isEmpty());
        verify(applicationRepository, never()).findAllLinkedApplicationsByLeadIds(any());
    }
}
