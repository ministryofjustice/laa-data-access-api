package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
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

    final static int NUMBER_OF_PREPOPULATED_APPLICATIONS = 8;
    List<ApplicationEntity> prePopulatedApplications;

    @BeforeEach
    void setUp() throws Exception {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");
        prePopulatedApplications = Instancio.ofList(ApplicationEntity.class)
                                .size(NUMBER_OF_PREPOPULATED_APPLICATIONS)
                                .generate(Select.field(ApplicationEntity::getStatus), gen -> gen.oneOf(ApplicationStatus.IN_PROGRESS, ApplicationStatus.SUBMITTED))
                                .set(Select.field(ApplicationEntity::getLinkedIndividuals), Set.of())
                                .generate(Select.field(ApplicationEntity::getApplicationReference), gen -> gen.oneOf("Appref1", "APPref2", "unknown"))
                                .set(Select.field(ApplicationEntity::getApplicationContent), map)
                                .create();
        applicationRepository.saveAll(prePopulatedApplications);
    }

    @Test void isStatusSpecification() {
        long expectedNumberOfInProgress = prePopulatedApplications.stream()
                .filter(a -> a.getStatus().equals(ApplicationStatus.IN_PROGRESS)).count();
        long expectedNumberOfSubmitted = prePopulatedApplications.stream()
                .filter(a -> a.getStatus().equals(ApplicationStatus.SUBMITTED)).count();
        assertNotEquals(0, expectedNumberOfInProgress);
        assertNotEquals(0, expectedNumberOfSubmitted);


        Specification<ApplicationSummaryEntity> inProgressEntities =
                ApplicationSummarySpecification.filterBy(ApplicationStatus.IN_PROGRESS, "");
        Specification<ApplicationSummaryEntity> submittedEntities =
                ApplicationSummarySpecification.filterBy(ApplicationStatus.SUBMITTED, "");

        assertEquals(expectedNumberOfInProgress, applicationSummaryRepository.count(inProgressEntities));
        applicationSummaryRepository
                .findAll(inProgressEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals(ApplicationStatus.IN_PROGRESS, a.getStatus())
                );

        assertEquals(expectedNumberOfSubmitted, applicationSummaryRepository.count(submittedEntities));
        applicationSummaryRepository
                .findAll(submittedEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals(ApplicationStatus.SUBMITTED, a.getStatus())
                );
    }

    @Test
    void isApplicationReferenceSpecification() {
        long expectedNumberOfAppref = prePopulatedApplications.stream()
                .filter(a ->
                        a.getApplicationReference().equals("Appref1")
                        || a.getApplicationReference().equals("APPref2")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> appref1Entities =
            ApplicationSummarySpecification.filterBy(null, "appref1");
        Specification<ApplicationSummaryEntity> appref2Entities =
                ApplicationSummarySpecification.filterBy(null, "appref2");

        assertEquals(expectedNumberOfAppref,
                applicationSummaryRepository.count(appref1Entities) +
                        applicationSummaryRepository.count(appref2Entities));

        applicationSummaryRepository
                .findAll(appref1Entities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals("appref1", a.getApplicationReference().toLowerCase())
                );

        applicationSummaryRepository
                .findAll(appref2Entities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals("appref2", a.getApplicationReference().toLowerCase())
                );
    }

    @Test
    void isApplicationReferencePartialFromStartSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(
                        a ->
                                a.getApplicationReference().startsWith("Appref")
                            || a.getApplicationReference().startsWith("APPref2")
                        ).count();

        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
            ApplicationSummarySpecification.filterBy(null, "appref");
        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals("appref", a.getApplicationReference().toLowerCase().substring(0,6))
                );
        assertEquals( expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isApplicationReferencePartialFromMiddleSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(a -> a.getApplicationReference().contains("ref")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(null, "REf");
        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getApplicationReference().toLowerCase().contains("ref"))
                );
        assertEquals(expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isApplicationReferencePartialFromEndSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(a -> a.getApplicationReference().endsWith("ef1")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(null, "eF1");

        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getApplicationReference().toLowerCase().endsWith("ef1"))
                );
        assertEquals(expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isApplicationReferenceNullSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(null, null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test
    void isApplicationReferenceBlankSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(null, ""));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test void isStatusSubmittedAndAppref1Specification() {
        long expectedNumberOfSubmittedAppref1 = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getStatus().equals(ApplicationStatus.SUBMITTED)
                        && a.getApplicationReference().endsWith("ref1"))
                .count();
        assertNotEquals(0, expectedNumberOfSubmittedAppref1);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(ApplicationStatus.SUBMITTED, "ref1");

        assertEquals(expectedNumberOfSubmittedAppref1, applicationSummaryRepository.count(apprefEntities));

        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getApplicationReference().toLowerCase().endsWith("ef1")
                                                    && a.getStatus().equals(ApplicationStatus.SUBMITTED))
                );
    }
}
