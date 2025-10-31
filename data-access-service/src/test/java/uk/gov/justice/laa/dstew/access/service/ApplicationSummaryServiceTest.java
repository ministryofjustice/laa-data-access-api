package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.StatusCodeLookupEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryServiceTest {

    @InjectMocks
    private ApplicationSummaryService classUnderTest;

    @Mock
    private ApplicationSummaryRepository repository;

    @Mock
    private ApplicationMapper mapper;

    @Test
    void shouldGetAllApplications() {
        StatusCodeLookupEntity firstStatusCodeLookupEntity = new StatusCodeLookupEntity();
        firstStatusCodeLookupEntity.setId(UUID.randomUUID());
        StatusCodeLookupEntity secondStatusCodeLookupEntity = new StatusCodeLookupEntity();
        secondStatusCodeLookupEntity.setId(UUID.randomUUID());

        ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
        firstEntity.setId(UUID.randomUUID());
        firstEntity.setStatusCodeLookupEntity(firstStatusCodeLookupEntity);

        ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
        secondEntity.setId(UUID.randomUUID());
        secondEntity.setStatusCodeLookupEntity(secondStatusCodeLookupEntity);

        ApplicationSummary firstSummary = new ApplicationSummary();
        firstSummary.setApplicationId(firstEntity.getId());
        firstSummary.setApplicationStatus("granted");
        ApplicationSummary secondSummary = new ApplicationSummary();
        secondSummary.setApplicationId(secondEntity.getId());
        secondSummary.setApplicationStatus("granted");

        when(repository.findByStatusCodeLookupEntity_Code(any())).thenReturn(List.of(firstEntity, secondEntity));
        when(mapper.toApplicationSummary(firstEntity)).thenReturn(firstSummary);
        when(mapper.toApplicationSummary(secondEntity)).thenReturn(secondSummary);

        List<ApplicationSummary> result = classUnderTest.getAllApplications("granted");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getApplicationId()).isEqualTo(firstEntity.getId());
        assertThat(result.get(1).getApplicationId()).isEqualTo(secondEntity.getId());

    }
}
