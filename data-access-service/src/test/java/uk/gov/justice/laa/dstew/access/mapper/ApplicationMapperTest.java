package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

@ExtendWith(MockitoExtension.class)
public class ApplicationMapperTest {

  @InjectMocks
  private ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  @Test
  void shouldMapApplicationEntityToApplication() {
    UUID id = UUID.randomUUID();
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(id);
    StatusCodeLookupEntity status = new StatusCodeLookupEntity();
    status.setCode("Pending");
    entity.setStatusEntity(status);
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("foo", "bar", "baz", 123));
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());

    Application result = applicationMapper.toApplication(entity);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getApplicationStatus()).isEqualTo("Pending");
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
  }

  @Test
  void shouldReturnNullWhenMappingNullEntity() {
    assertThat(applicationMapper.toApplication(null)).isNull();
  }

  @Test
  void shouldMapApplicationCreateRequestToApplicationEntity() {
    UUID statusId = UUID.randomUUID();
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatusId(statusId);
    req.setSchemaVersion(1);
    req.setApplicationContent(Map.of("foo", "bar"));

    ApplicationEntity result = applicationMapper.toApplicationEntity(req);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getStatusEntity().getId()).isEqualTo(statusId);
    assertThat(result.getSchemaVersion()).isEqualTo(1);
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
  }

  @Test
  void shouldReturnNullWhenMappingNullCreateRequest() {
    assertThat(applicationMapper.toApplicationEntity(null)).isNull();
  }

  @Test
  void shouldUpdateApplicationEntityWithoutOverwritingNulls() {
    ApplicationEntity entity = new ApplicationEntity();
    UUID originalStatusId = UUID.randomUUID();
    entity.setStatusId(originalStatusId);
    entity.setSchemaVersion(1);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls
    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatusEntity().getId()).isEqualTo(originalStatusId);
    assertThat(entity.getSchemaVersion()).isEqualTo(1);
  }

  @Test
  void shouldUpdateApplicationEntityWithNewValues() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatusId(UUID.randomUUID());
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("oldKey", "oldValue"));

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setStatusId(UUID.randomUUID());
    req.setSchemaVersion(2);
    req.setApplicationContent(Map.of("newKey", "newValue"));

    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatusEntity().getId()).isEqualTo(req.getStatusId());
    assertThat(entity.getSchemaVersion()).isEqualTo(2);
    assertThat(entity.getApplicationContent()).containsEntry("newKey", "newValue");
  }

  @Test
  void shouldThrowWhenApplicationCreateRequestContentCannotBeSerialized() {
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatusId(UUID.randomUUID());
    req.setApplicationContent(Map.of("key", new Object() {
      // Jackson cannot serialize anonymous object by default
    }));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplicationEntity(req));
  }

  @Test
  void shouldThrowWhenApplicationUpdateRequestContentCannotBeSerialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatusId(UUID.randomUUID());

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setApplicationContent(Map.of("key", new Object() {}));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.updateApplicationEntity(entity, req));
  }

  @Test
  void shouldThrowWhenApplicationEntityContentCannotBeDeserialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatusId(UUID.randomUUID());
    entity.setApplicationContent(Map.of("key", new Object() {}));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplication(entity));
  }
}
