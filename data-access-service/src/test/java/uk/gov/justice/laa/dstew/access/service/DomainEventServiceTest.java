package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

@ExtendWith(MockitoExtension.class)
public class DomainEventServiceTest {

    @InjectMocks
    private DomainEventService service;

    @Mock
    private DomainEventRepository repository;

    @Mock
    private DomainEventMapper mapper;

    @Mock
    private ObjectMapper objectMapper;

}
