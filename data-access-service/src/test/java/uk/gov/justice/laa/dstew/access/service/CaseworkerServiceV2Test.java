package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

public class CaseworkerServiceV2Test extends BaseServiceTest {

    @Autowired
    private CaseworkerService serviceUnderTest;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetAllCaseworkers {

        @ParameterizedTest
        @ValueSource(ints = {0, 10})
        public void givenRoleReader_whenGetAllCaseworkers_thenReturnCaseworkers(int count) {
            // given
            List<CaseworkerEntity> expectedCaseworkers = caseworkerFactory.createMultipleDefault(count);

            when(caseworkerRepository.findAll()).thenReturn(expectedCaseworkers);

            setSecurityContext(TestConstants.Roles.READER);

            // when
            List<Caseworker> actualCaseworkers = serviceUnderTest.getAllCaseworkers();

            // then
            verify(caseworkerRepository, times(1)).findAll();
            assertCaseworkerListsEqual(actualCaseworkers, expectedCaseworkers);
        }

        @Test
        public void givenNotRoleReader_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
            // given
            setSecurityContext(TestConstants.Roles.NO_ROLE);

            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.getAllCaseworkers())
                    .withMessageContaining("Access Denied");
            verify(caseworkerRepository, never()).findAll();
        }

        @Test
        public void givenNoRole_whenGetAllCaseworkers_thenThrowUnauthorizedException() {
            // given
            // when
            // then
            assertThatExceptionOfType(AuthorizationDeniedException.class)
                    .isThrownBy(() -> serviceUnderTest.getAllCaseworkers())
                    .withMessageContaining("Access Denied");
            verify(caseworkerRepository, never()).findAll();
        }

        private void assertCaseworkerListsEqual(List<Caseworker> actualList, List<CaseworkerEntity> expectedList) {

            assertThat(actualList).hasSameSizeAs(expectedList);

            for (CaseworkerEntity expected : expectedList) {
                boolean match = actualList.stream()
                        .anyMatch(actual -> {
                            try {
                                asserCaseworkerEqual(actual, expected);
                                return true;
                            } catch (AssertionError e) {
                                return false;
                            }
                        });
                assertThat(match)
                        .as("No matching CaseworkerEntity found for expected: " + expected)
                        .isTrue();
            }
        }

        private void asserCaseworkerEqual(Caseworker actual, CaseworkerEntity expected) {
            assertThat(actual.getId()).isEqualTo(expected.getId());
            assertThat(actual.getUsername()).isEqualTo(expected.getUsername());
        }
    }
}
