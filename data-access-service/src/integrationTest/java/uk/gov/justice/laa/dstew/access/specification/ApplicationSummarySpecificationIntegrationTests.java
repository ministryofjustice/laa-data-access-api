package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.instancio.Instancio;
import org.instancio.Select;
import org.instancio.generator.specs.OneOfArrayGeneratorSpec;
import org.instancio.generators.Generators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static uk.gov.justice.laa.dstew.access.Constants.POSTGRES_INSTANCE;

@Testcontainers
@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ApplicationSummarySpecificationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_INSTANCE);

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    ApplicationSummaryRepository applicationSummaryRepository;

    final static int NUMBER_OF_PREPOPULATED_APPLICATIONS = 5;
    List<ApplicationEntity> prePopulatedApplications;

    @BeforeEach
    void setUp() throws Exception {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");
        prePopulatedApplications = Instancio.ofList(ApplicationEntity.class)
                                .size(NUMBER_OF_PREPOPULATED_APPLICATIONS)
                                .generate(Select.field(ApplicationEntity::getStatus), gen -> gen.oneOf(ApplicationStatus.IN_PROGRESS, ApplicationStatus.SUBMITTED))
                                .generate(Select.field(ApplicationEntity::getApplicationReference), gen -> gen.oneOf("appref1", "appref2"))
                                .set(Select.field(ApplicationEntity::getApplicationContent), map)
                                .create();
        applicationRepository.saveAll(prePopulatedApplications);
    }

    @Test void isStatusSpecification() {
        long expectedNumberOfInProgress = prePopulatedApplications.stream().filter(a -> a.getStatus().equals(ApplicationStatus.IN_PROGRESS)).count();
        long expectedNumberOfSubmitted = prePopulatedApplications.stream().filter(a -> a.getStatus().equals(ApplicationStatus.SUBMITTED)).count();
        assertNotEquals(0, expectedNumberOfInProgress);
        assertNotEquals(0, expectedNumberOfSubmitted);

        var inProgressCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(ApplicationStatus.IN_PROGRESS, ""));
        var inSubmittedCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(ApplicationStatus.SUBMITTED, ""));

        assertEquals(expectedNumberOfInProgress, inProgressCount);
        assertEquals(expectedNumberOfSubmitted, inSubmittedCount);
    }

    @Test
    void isApplicationReferenceSpecification() {
        long expectedNumberOfAppref1 = prePopulatedApplications.stream().filter(a -> a.getApplicationReference().equals("appref1")).count();
        long expectedNumberOfAppref2 = prePopulatedApplications.stream().filter(a -> a.getApplicationReference().equals("appref2")).count();
        assertNotEquals(0, expectedNumberOfAppref1);
        assertNotEquals(0, expectedNumberOfAppref2);

        var appref1Count = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(null, "appref1"));
        var appref2Count = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(null, "appref2"));

        assertEquals(expectedNumberOfAppref1, appref1Count);
        assertEquals(expectedNumberOfAppref2, appref2Count);
    }

    @Test
    void isApplicationReferencePartialFromStartSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(a -> a.getApplicationReference().startsWith("appref")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        var apprefCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(null, "appref"));

        assertEquals(expectedNumberOfAppref, apprefCount);
    }
}
