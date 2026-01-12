package uk.gov.justice.laa.dstew.access.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.access.Constants.POSTGRES_INSTANCE;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

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

    private CaseworkerEntity createCaseworker(String username) {
        return CaseworkerEntity.builder()
                .createdAt(Instant.now())
                .username(username)
                .build();
    }

    private IndividualEntity createIndividual(
            String firstName,
            String lastName,
            LocalDate dob,
            String email
    ) {
        return IndividualEntity.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dob)
                .individualContent(Map.of("email", email))
                .build();
    }

    private ApplicationEntity createApplicationEntity(
            String laaReference,
            ApplicationStatus status,
            Map<String,Object> content,
            Set individuals,
            CaseworkerEntity caseworker
    ) {
        return ApplicationEntity.builder()
                .id(null)
                .laaReference(laaReference)
                .status(status)
                .applicationContent(content)
                .individuals(individuals)
                .caseworker(caseworker)
                .build();
    }

    void createPrePopulatedApplications(
            IndividualEntity individual1,
            IndividualEntity individual2,
            IndividualEntity individual3,
            IndividualEntity individual4,
            CaseworkerEntity caseworker1,
            CaseworkerEntity caseworker2
    ) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");

        prePopulatedApplications = new ArrayList<>();
        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual1, individual2), caseworker1)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "APPref2", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual2, individual3), caseworker2)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "unknown", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual3, individual4), caseworker1)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.SUBMITTED,
                        map, Set.of(individual4, individual1), caseworker2)
        );

        prePopulatedApplications.add (
                createApplicationEntity(
                        "Appref1", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual1, individual2), caseworker1)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "APPref2", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual2, individual3), caseworker2)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "unknown", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual3, individual4), caseworker1)
        );

        prePopulatedApplications.add(
                createApplicationEntity(
                        "Appref1", ApplicationStatus.IN_PROGRESS,
                        map, Set.of(individual4, individual1), caseworker2)
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

        CaseworkerEntity caseworker1 = createCaseworker("caseworker1");
        entityManager.persist(caseworker1);

        CaseworkerEntity caseworker2 = createCaseworker("caseworker2");
        entityManager.persist(caseworker2);

        createPrePopulatedApplications(individual1, individual2, individual3, individual4, caseworker1, caseworker2);

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
                ApplicationSummarySpecification.filterBy(ApplicationStatus.IN_PROGRESS, "", "", "", null);
        Specification<ApplicationSummaryEntity> submittedEntities =
                ApplicationSummarySpecification.filterBy(ApplicationStatus.SUBMITTED, "", "", "", null);

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
    void isLaaReferencePartialFromStartSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(
                        a ->
                                a.getLaaReference().startsWith("Appref")
                            || a.getLaaReference().startsWith("APPref2")
                        ).count();

        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
            ApplicationSummarySpecification.filterBy(null, "appref", null, null, null);
        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals("appref", a.getLaaReference().toLowerCase().substring(0,6))
                );
        assertEquals( expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isLaaReferencePartialFromMiddleSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(a -> a.getLaaReference().contains("ref")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(null, "REf", null, null, null);
        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getLaaReference().toLowerCase().contains("ref"))
                );
        assertEquals(expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isLaaReferencePartialFromEndSpecification() {
        long expectedNumberOfAppref =
                prePopulatedApplications.stream().filter(a -> a.getLaaReference().endsWith("ef1")).count();
        assertNotEquals(0, expectedNumberOfAppref);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(null, "eF1", null, null, null);

        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getLaaReference().toLowerCase().endsWith("ef1"))
                );
        assertEquals(expectedNumberOfAppref, applicationSummaryRepository.count(apprefEntities));
    }

    @Test
    void isAllFieldsNullSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, null, null, null, null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test
    void isLaaReferenceBlankSpecification() {
        long expectedNumberOfRecordsNoFilter =
                prePopulatedApplications.size();

        var recordCount = applicationSummaryRepository.count(ApplicationSummarySpecification.filterBy(
                null, "", null, null, null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test void isStatusSubmittedAndAppref1Specification() {
        long expectedNumberOfSubmittedAppref1 = prePopulatedApplications
                .stream()
                .filter(a ->
                        a.getStatus().equals(ApplicationStatus.SUBMITTED)
                        && a.getLaaReference().endsWith("ref1"))
                .count();
         assertNotEquals(0, expectedNumberOfSubmittedAppref1);

        Specification<ApplicationSummaryEntity> apprefEntities =
                ApplicationSummarySpecification.filterBy(
                        ApplicationStatus.SUBMITTED, "ref1", "","", null);

        assertEquals(expectedNumberOfSubmittedAppref1, applicationSummaryRepository.count(apprefEntities));

        applicationSummaryRepository
                .findAll(apprefEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertThat(a.getLaaReference().toLowerCase().endsWith("ef1")
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
                ApplicationSummarySpecification.filterBy(null, null, "rObert", null, null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();
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
                ApplicationSummarySpecification.filterBy(null, null, "oB", null, null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();
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
                ApplicationSummarySpecification.filterBy(null, null, "ERt", null, null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();
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
                null, null, "", null, null));

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
                ApplicationSummarySpecification.filterBy(null, null, null, "SOMe", null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();
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
                ApplicationSummarySpecification.filterBy(null, null, null, "THi", null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();

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
                ApplicationSummarySpecification.filterBy(null, null,  null, "Ng", null);

        long returnedNumberOfRecords = applicationSummaryRepository
                .findAll(entities, PageRequest.of(0, 20)).getTotalElements();
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
                null, null, null, "", null));

        assertEquals(expectedNumberOfRecordsNoFilter, recordCount);
    }

    @Test void isUserIdSpecification() {
        UUID expectedId = prePopulatedApplications.stream()
                .filter(a -> a.getCaseworker().getUsername().equals("caseworker1"))
                .findFirst()
                .get()
                .getCaseworker()
                .getId();

        long expectedNumberOfCaseworker = prePopulatedApplications.stream()
                .filter(a -> a.getCaseworker().getId().equals(expectedId)).count();
        assertNotEquals(0, expectedNumberOfCaseworker);

        Specification<ApplicationSummaryEntity> caseworkerEntities =
                ApplicationSummarySpecification.filterBy(null, "", "", "", expectedId);

        assertEquals(expectedNumberOfCaseworker, applicationSummaryRepository.count(caseworkerEntities));
        applicationSummaryRepository
                .findAll(caseworkerEntities, PageRequest.of(0, 10))
                .getContent().forEach(
                        a -> assertEquals(expectedId, a.getCaseworker().getId())
                );
    }
}
