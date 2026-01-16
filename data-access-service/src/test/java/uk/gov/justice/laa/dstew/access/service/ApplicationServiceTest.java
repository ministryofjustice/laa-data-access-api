package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  @InjectMocks private ApplicationService service;

  @Mock private ApplicationRepository repository;
  @Mock private CaseworkerRepository caseworkerRepository;
  @Mock private ApplicationValidations validator;
  @Mock private ApplicationMapper mapper;
  @Mock private ObjectMapper objectMapper;
  @Mock private DomainEventService domainEventService;
}
