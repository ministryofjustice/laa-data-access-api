package uk.gov.justice.laa.dstew.access.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ApplicationMapperTest {

  @InjectMocks
  private ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  @Test
  void shouldMapApplicationEntityToApplication() {
    UUID id = UUID.randomUUID();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);
    entity.setStatusId(UUID.randomUUID());
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of(
        "foo", "bar",
        "baz", 123
    ));
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());

    Application result = applicationMapper.toApplication(entity);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getStatusId()).isEqualTo(entity.getStatusId());
  }

  @Test
  void shouldMapApplicationCreateRequestToApplicationEntity() {
    UUID statusId = UUID.randomUUID();
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatusId(statusId);
    req.setSchemaVersion(1);

    ApplicationEntity result = applicationMapper.toApplicationEntity(req);

    assertThat(result).isNotNull();
    assertThat(result.getStatusId()).isEqualTo(statusId);
    assertThat(result.getSchemaVersion()).isEqualTo(1);
    assertThat(result.getApplicationContent()).isNotNull();
  }

  @Test
  void shouldUpdateApplicationEntityWithoutOverwritingNulls() {
    ApplicationEntity entity = new ApplicationEntity();
    UUID originalStatusId = UUID.randomUUID();
    entity.setStatusId(originalStatusId);
    entity.setSchemaVersion(1);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls
    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatusId()).isEqualTo(originalStatusId);
    assertThat(entity.getSchemaVersion()).isEqualTo(1);
  }
}
