package uk.gov.justice.laa.dstew.access.service.application.sharedAsserts;

import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class IndividualAssert {

    public static void assertIndividualCollectionsEqual(List<IndividualResponse> expectedList, Set<IndividualEntity> actualList) {

        assertThat(actualList).hasSameSizeAs(expectedList);

        for (IndividualResponse expected : expectedList) {
            boolean match = actualList.stream()
                    .anyMatch(actual -> {
                        try {
                            assertIndividualEqual(expected, actual);
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

    public static void assertIndividualEqual(IndividualResponse expected, IndividualEntity actual) {
        assertThat(actual.getFirstName()).isEqualTo(expected.getFirstName());
        assertThat(actual.getLastName()).isEqualTo(expected.getLastName());
        assertThat(actual.getDateOfBirth()).isEqualTo(expected.getDateOfBirth());
        assertThat(actual.getIndividualContent())
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(expected.getDetails());
    }
}
