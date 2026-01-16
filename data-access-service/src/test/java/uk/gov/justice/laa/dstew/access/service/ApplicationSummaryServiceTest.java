package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationSummaryMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;

@ExtendWith(MockitoExtension.class)
public class ApplicationSummaryServiceTest {

  @InjectMocks private ApplicationSummaryService classUnderTest;

  @Mock private ApplicationSummaryRepository repository;

  @Mock private ApplicationSummaryMapper mapper;
}
