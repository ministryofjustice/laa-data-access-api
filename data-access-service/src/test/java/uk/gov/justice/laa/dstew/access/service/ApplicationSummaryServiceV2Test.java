package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ApplicationSummaryServiceV2Test extends BaseServiceTest {

    @Autowired
    private ApplicationSummaryService serviceUnderTest;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplications {

        @ParameterizedTest
        @ValueSource(ints = {0, 10})
        public void givenPageZeroAndNoFilters_whenGetApplications_thenReturnApplications(int count) {
            // given
            List<ApplicationSummaryEntity> expectedApplications = applicationSummaryEntityFactory.createMultipleRandom(count);
            Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(expectedApplications);
            Pageable pageable = PageRequest.of(0, 10);

            setSecurityContext(TestConstants.Roles.READER);

            when(applicationSummaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

            // when
            List<ApplicationSummary> actualApplications = serviceUnderTest.getAllApplications(
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

        @Test
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
                    UUID.randomUUID(),
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
            verify(applicationSummaryRepository, never()).findAll();
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.getAllApplications(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    ))
                    .withMessageContaining("Access Denied");
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
            assertThat(expected.getStatus()).isEqualTo(actual.getApplicationStatus());
            assertThat(expected.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(actual.getCreatedAt().toInstant().truncatedTo(ChronoUnit.SECONDS));
            assertThat(expected.getModifiedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(actual.getModifiedAt().toInstant().truncatedTo(ChronoUnit.SECONDS));
            assertThat(expected.getCaseworker().getId()).isEqualTo(actual.getAssignedTo());
        }
    }
}