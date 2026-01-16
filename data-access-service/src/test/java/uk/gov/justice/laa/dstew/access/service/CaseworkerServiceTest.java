package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.mapper.CaseworkerMapper;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;

@ExtendWith(MockitoExtension.class)
public class CaseworkerServiceTest {
  @InjectMocks private CaseworkerService caseworkerService;

  @Mock private CaseworkerRepository caseworkerRepository;
  @Mock private CaseworkerMapper caseworkerMapper;
}
