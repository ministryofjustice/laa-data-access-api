package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.entity.StatusCodeLookupEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

import java.time.Instant;
import java.time.ZoneOffset;
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
    private ApplicationSummaryMapper mapper;

    @Test
    void shouldGetAllApplications() {
        StatusCodeLookupEntity firstStatusCodeLookupEntity = new StatusCodeLookupEntity();
        firstStatusCodeLookupEntity.setId(UUID.randomUUID());
        firstStatusCodeLookupEntity.setCode("DRAFT");

        ApplicationSummaryEntity firstEntity = new ApplicationSummaryEntity();
        firstEntity.setId(UUID.randomUUID());
        firstEntity.setApplicationReference("appRef1");
        firstEntity.setCreatedAt(Instant.now());
        firstEntity.setModifiedAt(Instant.now());
        firstEntity.setStatusCodeLookupEntity(firstStatusCodeLookupEntity);

        StatusCodeLookupEntity secondStatusCodeLookupEntity = new StatusCodeLookupEntity();
        secondStatusCodeLookupEntity.setId(UUID.randomUUID());
        secondStatusCodeLookupEntity.setCode("DRAFT");

        ApplicationSummaryEntity secondEntity = new ApplicationSummaryEntity();
        secondEntity.setId(UUID.randomUUID());
        secondEntity.setApplicationReference("appRef2");
        secondEntity.setCreatedAt(Instant.now());
        secondEntity.setModifiedAt(Instant.now());
        secondEntity.setStatusCodeLookupEntity(secondStatusCodeLookupEntity);

        ApplicationSummary firstSummary = new ApplicationSummary();
        firstSummary.setApplicationId(firstEntity.getId());
        firstSummary.setApplicationReference(firstEntity.getApplicationReference());
        firstSummary.setCreatedAt(firstEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
        firstSummary.setModifiedAt(firstEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
        firstSummary.setApplicationStatus(SubmissionStatus.fromValue(firstEntity.getStatusCodeLookupEntity().getCode()));
        ApplicationSummary secondSummary = new ApplicationSummary();
        secondSummary.setApplicationId(secondEntity.getId());
        secondSummary.setApplicationReference(secondEntity.getApplicationReference());
        secondSummary.setCreatedAt(secondEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
        secondSummary.setModifiedAt(secondEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
        secondSummary.setApplicationStatus(SubmissionStatus.fromValue(secondEntity.getStatusCodeLookupEntity().getCode()));

        Page<ApplicationSummaryEntity> pagedResponse =
                new PageImpl<ApplicationSummaryEntity>(List.of(firstEntity, secondEntity));

        when(repository.findAll(
                ArgumentMatchers.<Specification<ApplicationSummaryEntity>> any(),
                any(Pageable.class)
        ))
                .thenReturn(pagedResponse);

        when(mapper.toApplicationSummary(firstEntity)).thenReturn(firstSummary);
        when(mapper.toApplicationSummary(secondEntity)).thenReturn(secondSummary);

        List<ApplicationSummary> result = classUnderTest.getAllApplications("granted", 1,1);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getApplicationId()).isEqualTo(firstEntity.getId());
        assertThat(result.getFirst().getCreatedAt().compareTo(
                        firstEntity.getCreatedAt().atOffset(ZoneOffset.UTC)) == 0);
        assertThat(result.getFirst().getModifiedAt().compareTo(
                        firstEntity.getModifiedAt().atOffset(ZoneOffset.UTC)) == 0);
        assertThat(result.getFirst().getApplicationReference()).isEqualTo(firstEntity.getApplicationReference());
        assertThat(result.get(1).getApplicationId()).isEqualTo(secondEntity.getId());
        assertThat(result.get(1).getCreatedAt().compareTo(
                secondEntity.getCreatedAt().atOffset(ZoneOffset.UTC)) == 0);
        assertThat(result.get(1).getModifiedAt().compareTo(
                secondEntity.getModifiedAt().atOffset(ZoneOffset.UTC)) == 0);
        assertThat(result.get(1).getApplicationReference()).isEqualTo(secondEntity.getApplicationReference());

    }

}
