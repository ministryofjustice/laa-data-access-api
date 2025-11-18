package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
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

  List<ApplicationSummaryEntity> createInProgressApplicationSummaryEntities() {
    ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
    firstEntity.setId(UUID.randomUUID());
    firstEntity.setApplicationReference("appref1");
    firstEntity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
    secondEntity.setId(UUID.randomUUID());
    secondEntity.setApplicationReference("appref2");
    secondEntity.setStatus(ApplicationStatus.IN_PROGRESS);

    return List.of(firstEntity, secondEntity);
  }

  List<ApplicationSummary> createApplicationSummaries(
        List<ApplicationSummaryEntity> entities) {

    ApplicationSummary firstSummary = new ApplicationSummary();
    firstSummary.setApplicationId(entities.getFirst().getId());
    firstSummary.setApplicationReference(entities.getFirst().getApplicationReference());
    firstSummary.setApplicationStatus(entities.getFirst().getStatus());

    ApplicationSummary secondSummary = new ApplicationSummary();
    secondSummary.setApplicationId(entities.get(1).getId());
    secondSummary.setApplicationReference(entities.get(1).getApplicationReference());
    secondSummary.setApplicationStatus(entities.get(1).getStatus());

    return List.of(firstSummary, secondSummary);

  }

  @Test
  void shouldGetAllInProgressAndBlankReferenceApplications() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(1, 1);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    List<ApplicationSummary> result =
        classUnderTest.getAllApplications(ApplicationStatus.IN_PROGRESS,
                "",
                pageDetails.getPageNumber(),
                pageDetails.getPageSize());

    // Verify results
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(result.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

  @Test
  void shouldGetAllInProgressAndNullReferenceApplications() {

    List<ApplicationSummaryEntity> entities = createInProgressApplicationSummaryEntities();
    List<ApplicationSummary> summaries = createApplicationSummaries(entities);

    Pageable pageDetails = PageRequest.of(1, 1);

    // Wrap entities in a page
    Page<ApplicationSummaryEntity> pageResult = new PageImpl<>(entities);

    // Mock repository
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // Mock mapper
    when(mapper.toApplicationSummary(entities.getFirst())).thenReturn(summaries.getFirst());
    when(mapper.toApplicationSummary(entities.get(1))).thenReturn(summaries.get(1));

    List<ApplicationSummary> result =
            classUnderTest.getAllApplications(ApplicationStatus.IN_PROGRESS,
                    null,
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());

    // Verify results
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(result.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
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

    List<ApplicationSummary> result =
            classUnderTest.getAllApplications(null,
                    "appref",
                    pageDetails.getPageNumber(),
                    pageDetails.getPageSize());

    // Verify results
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getApplicationId()).isEqualTo(entities.getFirst().getId());
    assertThat(result.get(1).getApplicationId()).isEqualTo(entities.get(1).getId());
  }

}
