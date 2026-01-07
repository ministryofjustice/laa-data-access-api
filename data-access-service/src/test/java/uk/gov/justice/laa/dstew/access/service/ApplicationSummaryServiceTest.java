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

}
