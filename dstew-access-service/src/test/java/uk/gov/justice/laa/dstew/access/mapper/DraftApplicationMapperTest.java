package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.DraftApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.DraftApplication;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DraftApplicationUpdateRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class DraftApplicationMapperTest {

    @InjectMocks
    private DraftApplicationMapper applicationMapper = new DraftApplicationMapperImpl();

    @Test
    void shouldMapDraftApplicationCreateRequestToDraftApplicationEntity() {

        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        DraftApplicationCreateRequest entity = new DraftApplicationCreateRequest();
        entity.setClientId(clientId);
        entity.setProviderId(providerId);
        entity.setAdditionalData(Map.of("key1", "value1"));

        DraftApplicationEntity result = applicationMapper.toDraftApplicationEntity(entity);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getProviderId()).isEqualTo(providerId);
        assertThat(result.getAdditionalData().get("key1")).isEqualTo("value1");
    }

    @Test
    void shouldMapDraftApplicationEntityToDraftApplication() {

        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        DraftApplicationEntity entity = new DraftApplicationEntity();
        entity.setClientId(clientId);
        entity.setProviderId(providerId);
        entity.setAdditionalData(Map.of("key1", "value1"));

        DraftApplication result = applicationMapper.toDraftApplication(entity);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getProviderId()).isEqualTo(providerId);
        assertThat(result.getAdditionalData().get("key1")).isEqualTo("value1");
    }

    @Test
    void shouldMapDraftApplicationUpdateRequestToDraftApplicationEntity() {

        UUID clientId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        DraftApplicationUpdateRequest entity = new DraftApplicationUpdateRequest();
        entity.setClientId(clientId);
        entity.setProviderId(providerId);
        entity.setAdditionalData(Map.of("key1", "value1"));

        DraftApplicationEntity result = new DraftApplicationEntity();
        applicationMapper.updateApplicationEntity(result, entity);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo(clientId);
        assertThat(result.getProviderId()).isEqualTo(providerId);
        assertThat(result.getAdditionalData().get("key1")).isEqualTo("value1");
    }

}