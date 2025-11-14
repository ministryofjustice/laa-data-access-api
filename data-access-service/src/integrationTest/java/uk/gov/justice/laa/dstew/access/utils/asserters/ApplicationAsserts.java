package uk.gov.justice.laa.dstew.access.utils.asserters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationAsserts {

    public static void assertApplicationsEqual(List<ApplicationEntity> expected, List<Application> actual) {
        assertEquals(expected.size(), actual.size());
    }

    public static void assertApplicationEqual(ApplicationEntity expected, Application actual) {
        assertEquals(expected.getId(), actual.getId());
    }
}
