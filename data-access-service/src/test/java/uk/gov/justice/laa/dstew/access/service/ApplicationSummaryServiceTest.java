package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  @Test
  void shouldGetAllApplications() {
    ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
    firstEntity.setId(UUID.randomUUID());
    firstEntity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
    secondEntity.setId(UUID.randomUUID());
    secondEntity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationSummary firstSummary = new ApplicationSummary();
    firstSummary.setApplicationId(firstEntity.getId());
    firstSummary.setApplicationStatus(ApplicationStatus.valueOf(ApplicationStatus.IN_PROGRESS.name()));

    ApplicationSummary secondSummary = new ApplicationSummary();
    secondSummary.setApplicationId(secondEntity.getId());
    secondSummary.setApplicationStatus(ApplicationStatus.valueOf(ApplicationStatus.IN_PROGRESS.name()));

    when(repository.findByStatusCodeLookupEntity_Code(any(), any())).thenReturn(List.of(firstEntity, secondEntity));
    when(mapper.toApplicationSummary(firstEntity)).thenReturn(firstSummary);
    when(mapper.toApplicationSummary(secondEntity)).thenReturn(secondSummary);

    List<ApplicationSummary> result = classUnderTest.getAllApplications(ApplicationStatus.valueOf("IN_PROGRESS"), 1, 1);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getApplicationId()).isEqualTo(firstEntity.getId());
    assertThat(result.get(1).getApplicationId()).isEqualTo(secondEntity.getId());
  }
}
