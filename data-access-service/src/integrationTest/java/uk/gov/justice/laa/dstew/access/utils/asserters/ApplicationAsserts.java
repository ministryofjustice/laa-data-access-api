package uk.gov.justice.laa.dstew.access.utils.asserters;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ApplicationAsserts {

    public static void assertApplicationEqual(ApplicationEntity expected, ApplicationEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "modifiedAt")
                .isEqualTo(actual);
    }

    // TODO: check whether we align status and applicationStatus.
    public static void assertApplicationEqual(ApplicationEntity expected, Application actual) {

        assertEquals(expected.getId(), actual.getId());

        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFields("id", "createdAt", "modifiedAt", "status", "caseworker", "individuals")
                .isEqualTo(actual);

        assertEquals(expected.getStatus(), actual.getApplicationStatus());

        assertThat(expected.getIndividuals())
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFields("id", "createdAt", "modifiedAt", "individualContent")
                .isEqualTo(actual.getIndividuals());
    }

    public static void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "modifiedAt", "status", "individuals")
                .isEqualTo(actual);

        assertThat(expected.getIndividuals())
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFields("id", "createdAt", "modifiedAt", "details")
                .isEqualTo(actual.getIndividuals());
    }

    public static void assertApplicationEqual(ApplicationEntity expected, ApplicationSummary actual) {
        assertEquals(expected.getId(), actual.getApplicationId());
        assertEquals(expected.getApplicationReference(), actual.getApplicationReference());
        assertEquals(expected.getStatus(), actual.getApplicationStatus());
    }

    public static void assertApplicationListsEqual(List<ApplicationEntity> expected, List<ApplicationSummary> actual) {
        assertEquals(expected.size(), actual.size());
        // seems to work for now - might need to ignore order, etc..
        for (int i = 0; i < expected.size(); i++) {
            assertApplicationEqual(expected.get(i), actual.get(i));
        }
    }
}
