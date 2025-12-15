package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;


@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryServiceTest {

  @InjectMocks
  private ApplicationSummaryService classUnderTest;

  @Mock
  private ApplicationSummaryRepository repository;

  @Mock
  private ApplicationSummaryMapper mapper;

  private Set<IndividualEntity> createIndividuals() {
    IndividualEntity firstIndividual = new IndividualEntity();
    firstIndividual.setId(UUID.randomUUID());
    firstIndividual.setFirstName("Dave");
    firstIndividual.setLastName("Young");

    IndividualEntity secondIndividual = new IndividualEntity();
    secondIndividual.setId(UUID.randomUUID());
    secondIndividual.setFirstName("Andrea");
    secondIndividual.setLastName("Smith");

    return Set.of(firstIndividual, secondIndividual);
  }

  private List<ApplicationSummaryEntity> createInProgressApplicationSummaryEntities() {
    ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
    firstEntity.setId(UUID.randomUUID());
    firstEntity.setLaaReference("appref1");
    firstEntity.setStatus(ApplicationStatus.IN_PROGRESS);
    firstEntity.setIndividuals(createIndividuals());

    ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
    secondEntity.setId(UUID.randomUUID());
    secondEntity.setLaaReference("appref2");
    secondEntity.setStatus(ApplicationStatus.IN_PROGRESS);
    secondEntity.setIndividuals(createIndividuals());

    return List.of(firstEntity, secondEntity);
  }

  private List<ApplicationSummary> createApplicationSummaries(
        List<ApplicationSummaryEntity> entities) {

    ApplicationSummary firstSummary = new ApplicationSummary();
    firstSummary.setApplicationId(entities.getFirst().getId());
    firstSummary.setLaaReference(entities.getFirst().getLaaReference());
    firstSummary.setApplicationStatus(entities.getFirst().getStatus());

    ApplicationSummary secondSummary = new ApplicationSummary();
    secondSummary.setApplicationId(entities.get(1).getId());
    secondSummary.setLaaReference(entities.get(1).getLaaReference());
    secondSummary.setApplicationStatus(entities.get(1).getStatus());

    return List.of(firstSummary, secondSummary);

  }

  @Test
  void shouldGetAllInProgressAndBlankReferenceApplicationAndName() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(0, 5);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    var result =
        classUnderTest.getAllApplications(ApplicationStatus.IN_PROGRESS,
                "",
                "",
                "",
                null,
                pageDetails.getPageNumber(),
                pageDetails.getPageSize());

    // Verify results
    List<ApplicationSummary> applicationsReturned = result.stream().toList();
    assertEquals(2, result.getTotalElements());
    assertThat(applicationsReturned.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(applicationsReturned.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

  @Test
  void shouldGetAllInProgressAndNullReferenceApplicationsAndName() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(0, 5);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    var result =
            classUnderTest.getAllApplications(ApplicationStatus.IN_PROGRESS,
                    null,
                    null,
                    null,
                    null,
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());

    // Verify results
    List<ApplicationSummary> applicationsReturned = result.stream().toList();
    assertEquals(2, result.getTotalElements());
    assertThat(applicationsReturned.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(applicationsReturned.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

  @Test
  void shouldGetAllApplicationReferenceApplications() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(0, 5);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    var result =
            classUnderTest.getAllApplications(null,
                    "appref",
                    null,
                    null,
                    null,
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());

    // Verify results
    List<ApplicationSummary> applicationsReturned = result.stream().toList();
    assertEquals(2, result.getTotalElements());
    assertThat(applicationsReturned.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(applicationsReturned.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

  @Test
  void shouldGetAllFirstNameApplications() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(0, 5);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    var result =
            classUnderTest.getAllApplications(null,
                    null,
                    "Dave",
                    null,
                    null,
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());

    // Verify results
    List<ApplicationSummary> applicationsReturned = result.stream().toList();
    assertEquals(2, result.getTotalElements());
    assertThat(applicationsReturned.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(applicationsReturned.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

  @Test
  void shouldGetAllLastNameApplications() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(0, 5);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    var result =
            classUnderTest.getAllApplications(null,
                    null,
                    null,
                    "Young",
                    null,
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());


    // Verify results
    List<ApplicationSummary> applicationsReturned = result.stream().toList();
    assertEquals(2, result.getTotalElements());
    assertThat(applicationsReturned.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(applicationsReturned.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }
}
