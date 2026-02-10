package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

public class GetIndividualsTest extends BaseServiceTest {

    @Autowired
    private IndividualsService individualsService;

    @ParameterizedTest
    @MethodSource("pagingParameters")
    void givenPagingParameters_whenGetIndividuals_thenRepositoryCalledWithCorrectPageable(int page, int pageSize, int expectedPage, int expectedPageSize, int entityCount, int totalElements) {
        setSecurityContext(TestConstants.Roles.READER);
        Pageable pageable = PageRequest.of(expectedPage, expectedPageSize);
        List<IndividualEntity> entities = entityCount == 0 ? Collections.emptyList() :
                entityCount == 1 ? List.of(individualEntityFactory.createDefault()) :
                List.of(individualEntityFactory.createDefault(), individualEntityFactory.createDefault());
        Page<IndividualEntity> entityPage = new PageImpl<>(entities, pageable, totalElements);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(page, pageSize);

        assertThat(result.getContent().size()).isEqualTo(entities.size());
        assertThat(result.getTotalElements()).isEqualTo(totalElements);
        assertThat(result.getNumber()).isEqualTo(expectedPage);
        assertThat(result.getSize()).isEqualTo(expectedPageSize);
        verify(individualRepository, times(1)).findAll(pageable);
        assertIndividualListMatchesEntities(result.getContent(), entities);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> pagingParameters() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(0, 10, 0, 10, 0, 0), // empty page
            org.junit.jupiter.params.provider.Arguments.of(0, 2, 0, 2, 2, 3), // first page, 2 results, 3 total
            org.junit.jupiter.params.provider.Arguments.of(1, 2, 1, 2, 1, 3), // second page, 1 result, 3 total
            org.junit.jupiter.params.provider.Arguments.of(-1, 10, 0, 10, 0, 0), // negative page, empty
            org.junit.jupiter.params.provider.Arguments.of(1, 0, 1, 10, 0, 0), // zero pageSize, empty
            org.junit.jupiter.params.provider.Arguments.of(1, 101, 1, 100, 0, 0) // pageSize > 100 capped
        );
    }

    @Test
    public void givenUserWithNoReaderRole_whenGetIndividuals_thenThrowUnauthorizedException() {
        // given
        setSecurityContext(TestConstants.Roles.NO_ROLE);
        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> individualsService.getIndividuals(0, 10))
                .withMessageContaining("Access Denied");
        verify(individualRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    public void givenNoUser_whenGetIndividuals_thenThrowUnauthorizedException() {
        // when / then
        assertThatExceptionOfType(AuthorizationDeniedException.class)
                .isThrownBy(() -> individualsService.getIndividuals(0, 10))
                .withMessageContaining("Access Denied");
        verify(individualRepository, never()).findAll(any(Pageable.class));
    }

    private void assertIndividualListMatchesEntities(List<Individual> actualList, List<IndividualEntity> expectedEntities) {
        assertThat(actualList).hasSameSizeAs(expectedEntities);
        for (IndividualEntity expected : expectedEntities) {
            boolean match = actualList.stream()
                    .anyMatch(actual -> {
                        try {
                            assertIndividualEqual(actual, expected);
                            return true;
                        } catch (AssertionError e) {
                            return false;
                        }
                    });
            assertThat(match)
                    .as("No matching IndividualEntity found for expected: " + expected)
                    .isTrue();
        }
    }

    private void assertIndividualEqual(Individual actual, IndividualEntity expected) {
        assertThat(actual.getFirstName()).isEqualTo(expected.getFirstName());
        assertThat(actual.getLastName()).isEqualTo(expected.getLastName());
    }
}
