package uk.gov.justice.laa.dstew.access.utils.asserters;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ApplicationAsserts {

    public static void assertApplicationsEqual(List<ApplicationEntity> expected, List<Application> actual) {
        assertEquals(expected.size(), actual.size());
    }

    public static void assertApplicationEqual(ApplicationEntity expected, Application actual) {
        assertEquals(expected.getId(), actual.getId());
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "modifiedAt", "applicationReference")
                .isEqualTo(actual);
    }

    public static void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "modifiedAt", "status")
                .isEqualTo(actual);
    }

    public static void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "modifiedAt", "status")
                .isEqualTo(actual);
    }
}
