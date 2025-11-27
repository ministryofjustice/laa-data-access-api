package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.*;

import jakarta.persistence.EntityManager;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.Ignore;
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
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.junit.jupiter.api.Assertions.*;
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

    @Autowired
    private EntityManager entityManager;

    List<ApplicationEntity> prePopulatedApplications;

    private IndividualEntity createIndividual(
            String firstName,
            String lastName,
            LocalDate dob,
            String email
    ) {
        IndividualEntity individual = new IndividualEntity();
        individual.setFirstName(firstName);
        individual.setLastName(lastName);
        individual.setDateOfBirth(dob);
        individual.setIndividualContent(Map.of("email", email));

        return individual;
    }

    private ApplicationEntity createApplicationEntity(
            String applicationReference,
            ApplicationStatus status,
            Map<String,Object> content,
            Set individuals
    ) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(null);
        entity.setApplicationReference(applicationReference);
        entity.setStatus(status);
        entity.setApplicationContent(content);
        entity.setIndividuals(individuals);
        return entity;
    }

    void createPrePopulatedApplications(
            IndividualEntity individual1,
            IndividualEntity individual2,
            IndividualEntity individual3,
            IndividualEntity individual4
    ) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");

        prePopulatedApplications = new ArrayList<>();
        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual1))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "APPref2", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual2))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "unknown", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual3))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual4))
        );

        prePopulatedApplications.add (
                createApplicationEntity(
                        "Appref1", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual1))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "APPref2", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual2))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "unknown", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual3))
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual4))
        );

    }
    @BeforeEach
    void setUp() throws Exception {

        IndividualEntity individual1 = createIndividual(
                "Rob",
                "someone",
                LocalDate.of(1990, 1, 1),
                "rs1@example.com");
        entityManager.persist(individual1);

        IndividualEntity individual2 = createIndividual(
                "robert",
                "something",
                        LocalDate.of(1990, 1, 1),
                 "rs2@example.com");
        entityManager.persist(individual2);

        IndividualEntity individual3 = createIndividual(
                "Bert",
                "everything",
                LocalDate.of(1990, 1, 1),
                "rs3@example.com");
        entityManager.persist(individual3);

        IndividualEntity individual4 = createIndividual(
                "bobby",
                "everyone",
                LocalDate.of(1990, 1, 1),
                "rs4@example.com");
        entityManager.persist(individual4);

        createPrePopulatedApplications(individual1, individual2, individual3, individual4);
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
                ApplicationSummarySpecification.filterBy(ApplicationStatus.IN_PROGRESS, "", "", "");
        Specification<ApplicationSummaryEntity> submittedEntities =
                ApplicationSummarySpecification.filterBy(ApplicationStatus.SUBMITTED, "", "", "");

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
    void isApplicationReferencePartialFromStartSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(
                        a ->
                                a.getApplicationReference().startsWith("Appref")
                            || a.getApplicationReference().startsWith("APPref2")
                        ).count();

        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
            ApplicationSummarySpecification.filterBy(null, "appref", null, null);
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
                ApplicationSummarySpecification.filterBy(null, "REf", null, null);
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
                ApplicationSummarySpecification.filterBy(null, "eF1", null, null);

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

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, null, null, null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test
    void isApplicationReferenceBlankSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, "", null, null));

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
                ApplicationSummarySpecification.filterBy(
                        ApplicationStatus.SUBMITTED, "ref1", "","");

        assertEquals(expectedNumberOfSubmittedAppref1, applicationSummaryRepository.count(apprefEntities));

        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getApplicationReference().toLowerCase().endsWith("ef1")
                                                    && a.getStatus().equals(ApplicationStatus.SUBMITTED))
                );
    }

    @Test
    void isFirstNameBeginsWithSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getFirstName().startsWith("robert")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null, "rObert", null);

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
            a -> assertTrue(a.getIndividuals()
                                         .stream()
                                         .anyMatch(i -> i.getFirstName().toLowerCase().startsWith("robert"))
                                        )
                                    );
    }

    @Test
    void isFirstNameContainedSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getFirstName().contains("ob")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null, "oB", null);

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
                        a -> assertTrue(a.getIndividuals()
                                .stream()
                                .anyMatch(i -> i.getFirstName().toLowerCase().contains("ob"))
                        )
                );
    }

    @Test
    void isFirstNameEndsWithSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getFirstName().endsWith("ert")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null, "ERt", null);

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
                        a -> assertTrue(a.getIndividuals()
                                .stream()
                                .anyMatch(i -> i.getFirstName().toLowerCase().endsWith("ert"))
                        )
                );
    }

    @Test
    void isFirstNameBlankSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, null, "", null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test
    void isLastNameBeginsWithSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getLastName().startsWith("some")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null, null, "SOMe");

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
                        a -> assertTrue(a.getIndividuals()
                                .stream()
                                .anyMatch(i -> i.getLastName().toLowerCase().startsWith("some"))
                        )
                );
    }

    @Test
    void isLastNameContainedSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getLastName().contains("thi")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null, null, "THi");

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
                        a -> assertTrue(a.getIndividuals()
                                .stream()
                                .anyMatch(i -> i.getLastName().toLowerCase().contains("thi"))
                        )
                );
    }

    @Test
    void isLastNameEndsWithSpecification() {
        long expectedNumberOfGeneratedRecords = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getIndividuals()
                                .stream()
                                .anyMatch(i-> i.getLastName().endsWith("ng")))
                .count();

        assertNotEquals(0, expectedNumberOfGeneratedRecords);

        Specification<ApplicationSummaryEntity> entities =
                ApplicationSummarySpecification.filterBy(null, null,  null, "Ng");

        long returnedNumberOfRecords = applicationSummaryRepository.count(entities);
        assertEquals(expectedNumberOfGeneratedRecords, returnedNumberOfRecords);

        applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20))
                .getContent().forEach(
                        a -> assertTrue(a.getIndividuals()
                                .stream()
                                .anyMatch(i -> i.getLastName().toLowerCase().endsWith("ng"))
                        )
                );
    }

    @Test
    void isLastNameBlankSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, null, null, ""));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }
}
