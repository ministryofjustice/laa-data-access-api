package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.DraftApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.DraftApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.DraftApplicationValidations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DraftApplicationServiceTest {

    @InjectMocks
    private DraftApplicationService classUnderTest;

    @Mock
    private DraftApplicationValidations validator;

    @Mock
    private DraftApplicationRepository repository;

    @Mock
    private  DraftApplicationMapper mapper;

    @Test
    void shouldCreateApplication(){
        UUID createdId = UUID.randomUUID();
        DraftApplicationCreateRequest createRequest = new DraftApplicationCreateRequest();
        DraftApplicationEntity savedItem = new DraftApplicationEntity();
        DraftApplicationEntity mappedItem = new DraftApplicationEntity();
        createRequest.setProviderId(UUID.randomUUID());
        createRequest.setClientId(UUID.randomUUID());
        mappedItem.setId(createdId);
        mappedItem.setClientId(createRequest.getClientId());
        savedItem.setId(mappedItem.getId());
        savedItem.setClientId(mappedItem.getClientId());

        doNothing().when(validator).checkCreateRequest(any());
        when(mapper.toDraftApplicationEntity(any())).thenReturn(mappedItem);
        when(repository.save(any())).thenReturn(savedItem);

        UUID result = classUnderTest.createApplication(createRequest);
        assertThat(result).isEqualTo(createdId);
    }

    @Test
    void shouldGetApplicationById() {
        DraftApplicationEntity foundItem = new DraftApplicationEntity();
        DraftApplication mappedItem = new DraftApplication();

        mappedItem.setId(UUID.randomUUID());
        mappedItem.setClientId(UUID.randomUUID());
        foundItem.setId(mappedItem.getId());
        foundItem.setClientId(mappedItem.getClientId());

        when(repository.findById(any())).thenReturn(Optional.of(foundItem));
        when(mapper.toDraftApplication(any())).thenReturn(mappedItem);

        DraftApplication result = classUnderTest.getApplicationById(mappedItem.getId());
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(foundItem.getId());
    }

    @Test
    void shouldThrowExceptionIfIdNotFound() {
        DraftApplication mappedItem = new DraftApplication();

        mappedItem.setId(UUID.randomUUID());
        mappedItem.setClientId(UUID.randomUUID());

        when(repository.findById(any())).thenThrow(ApplicationNotFoundException.class);

        assertThrows(ApplicationNotFoundException.class,
                () -> classUnderTest.getApplicationById(mappedItem.getId()));
    }

    @Test
    void shouldUpdateApplication() {
        UUID createdId = UUID.randomUUID();
        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();
        DraftApplicationEntity updatedItem = new DraftApplicationEntity();
        DraftApplicationEntity mappedItem = new DraftApplicationEntity();
        DraftApplicationEntity foundItem = new DraftApplicationEntity();

        request.setId(UUID.randomUUID());
        request.setProviderId(UUID.randomUUID());
        request.setClientId(UUID.randomUUID());

        foundItem.setId(createdId);
        foundItem.setClientId(request.getClientId());
        mappedItem.setId(createdId);
        mappedItem.setClientId(request.getClientId());
        updatedItem.setId(createdId);
        updatedItem.setClientId(mappedItem.getClientId());

        when(repository.findById(any())).thenReturn(Optional.of(foundItem));
        doNothing().when(validator).checkDraftApplicationUpdateRequest(any(), any());
        when(repository.save(any())).thenReturn(updatedItem);

        var result = classUnderTest.updateApplication(request.getId(), request);
        assertThat(result.getClientId()).isEqualTo(request.getClientId());
    }

    @Test
    void shouldThrowExceptionIfUpdateApplicationNotFound() {
        DraftApplicationUpdateRequest request = new DraftApplicationUpdateRequest();

        request.setId(UUID.randomUUID());
        request.setProviderId(UUID.randomUUID());
        request.setClientId(UUID.randomUUID());

        when(repository.findById(any())).thenThrow(ApplicationNotFoundException.class);

        assertThrows(ApplicationNotFoundException.class,
                () -> classUnderTest.updateApplication(request.getId(), request));
    }

}
