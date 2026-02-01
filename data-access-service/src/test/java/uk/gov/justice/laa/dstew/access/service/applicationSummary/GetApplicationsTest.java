package uk.gov.justice.laa.dstew.access.service.applicationSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortFields;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    private static Stream<Arguments> sortParameters() {
      return Stream.of(
        Arguments.of(ApplicationSortBy.LAST_UPDATED_DATE, ApplicationOrderBy.ASC),
        Arguments.of(ApplicationSortBy.SUBMITTED_DATE, ApplicationOrderBy.ASC),
        Arguments.of(ApplicationSortBy.LAST_UPDATED_DATE, ApplicationOrderBy.DESC),
        Arguments.of(ApplicationSortBy.SUBMITTED_DATE, ApplicationOrderBy.DESC)
      );
    }

    @ParameterizedTest
    @MethodSource("sortParameters")
    public void givenPageZeroAndNoFiltersAndDataSorted_whenGetApplications_thenReturnSortedApplications(
            ApplicationSortBy sortBy,
            ApplicationOrderBy orderBy
    ) {
        // given
        List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(5);
        int lastSubmittedDayCount = 0;
        int lastModifiedDayCount = 10;
        Instant referenceDate = Instant.now();

        for (ApplicationSummaryEntity applicationSummaryEntity : expectedApplications) {
            applicationSummaryEntity.setSubmittedAt(referenceDate.plus(lastSubmittedDayCount++, ChronoUnit.DAYS));
            applicationSummaryEntity.setModifiedAt(referenceDate.plus(lastModifiedDayCount--, ChronoUnit.DAYS));
        }

        Sort sortOrder = Sort.by(
                Sort.Direction.fromString(orderBy.getValue()),
                String.valueOf(ApplicationSortFields.valueOf(sortBy.getValue()))
        );

        Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);
        Pageable pageable = PageRequest.of(0, 4, sortOrder);

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
                sortBy,
                orderBy,
                0,
                4
        ).stream().toList();

        // then
        verify(applicationSummaryRepository, times(1)).findAll(any(Specification.class), eq(pageable));
        assertApplicationSummaryListsEqual(actualApplications, expectedApplications);
    }

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
                0,
                10
        ).stream().toList();

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
                0,
                10
        ).stream().toList();

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
                0,
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
}
