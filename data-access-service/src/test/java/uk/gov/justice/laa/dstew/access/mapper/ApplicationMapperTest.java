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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
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
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("foo", "bar", "baz", 123));
    entity.setCreatedAt(Instant.now());
    entity.setModifiedAt(Instant.now());

    Application result = applicationMapper.toApplication(entity);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getApplicationStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
    assertThat(result.getApplicationContent()).containsEntry("foo", "bar");
  }

  @Test
  void shouldReturnNullWhenMappingNullEntity() {
    assertThat(applicationMapper.toApplication(null)).isNull();
  }

  @Test
  void shouldMapApplicationCreateRequestToApplicationEntity() {
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatus(ApplicationStatus.SUBMITTED);
    req.setApplicationContent(Map.of("foo", "bar"));

    ApplicationEntity result = applicationMapper.toApplicationEntity(req);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
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
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest(); // all nulls
    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatus()).isEqualTo(ApplicationStatus.IN_PROGRESS);
  }

  @Test
  void shouldUpdateApplicationEntityWithNewValues() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setSchemaVersion(1);
    entity.setApplicationContent(Map.of("oldKey", "oldValue"));

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setStatus(ApplicationStatus.SUBMITTED);
    req.setApplicationContent(Map.of("newKey", "newValue"));

    applicationMapper.updateApplicationEntity(entity, req);

    assertThat(entity.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    entity.setSchemaVersion(1);
    assertThat(entity.getApplicationContent()).containsEntry("newKey", "newValue");
  }

  @Test
  void shouldThrowWhenApplicationCreateRequestContentCannotBeSerialized() {
    ApplicationCreateRequest req = new ApplicationCreateRequest();
    req.setStatus(ApplicationStatus.IN_PROGRESS);
    req.setApplicationContent(Map.of("key", new Object() {
      // Jackson cannot serialize anonymous object by default
    }));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplicationEntity(req));
  }

  @Test
  void shouldThrowWhenApplicationUpdateRequestContentCannotBeSerialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);

    ApplicationUpdateRequest req = new ApplicationUpdateRequest();
    req.setApplicationContent(Map.of("key", new Object() {
    }));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.updateApplicationEntity(entity, req));
  }

  @Test
  void shouldThrowWhenApplicationEntityContentCannotBeDeserialized() {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.IN_PROGRESS);
    entity.setApplicationContent(Map.of("key", new Object() {
    }));

    assertThrows(IllegalArgumentException.class, () -> applicationMapper.toApplication(entity));
  }

  @Test
  void shouldSetTimestampsAndSchemaVersionOnCreateAndUpdate() throws InterruptedException {
    ApplicationCreateRequest createReq = new ApplicationCreateRequest();
    createReq.setStatus(ApplicationStatus.SUBMITTED);
    createReq.setApplicationContent(Map.of("key", "value"));

    Instant beforeCreate = Instant.now();
    ApplicationEntity entity = applicationMapper.toApplicationEntity(createReq);
    Instant afterCreate = Instant.now();

    assertThat(entity.getSchemaVersion()).isEqualTo(1);
    assertThat(entity.getCreatedAt()).isBetween(beforeCreate, afterCreate);
    assertThat(entity.getModifiedAt()).isBetween(beforeCreate, afterCreate);

    Thread.sleep(10); // ensure time difference
    ApplicationUpdateRequest updateReq = new ApplicationUpdateRequest();
    updateReq.setApplicationContent(Map.of("key", "newValue"));

    Instant beforeUpdate = Instant.now();
    applicationMapper.updateApplicationEntity(entity, updateReq);
    Instant afterUpdate = Instant.now();

    assertThat(entity.getCreatedAt()).isBetween(beforeCreate, afterCreate);

    assertThat(entity.getModifiedAt()).isBetween(beforeUpdate, afterUpdate);

    assertThat(entity.getApplicationContent()).containsEntry("key", "newValue");
  }
}
