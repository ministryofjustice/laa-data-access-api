package uk.gov.justice.laa.dstew.access.service;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

@ExtendWith(MockitoExtension.class)
public class DomainEventServiceTest {

  @InjectMocks private DomainEventService service;

  @Mock private DomainEventRepository repository;

  @Mock private DomainEventMapper mapper;

  @Mock private ObjectMapper objectMapper;
}
