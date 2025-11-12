package uk.gov.justice.laa.dstew.access.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

class ApplicationEntityTest {

  @Test
  void testGettersAndSetters() {
    UUID id = UUID.randomUUID();
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    Integer schemaVersion = 1;
    Instant createdAt = Instant.now();
    Instant modifiedAt = Instant.now();
    ApplicationStatus status = ApplicationStatus.IN_PROGRESS;

    ApplicationEntity entity = new ApplicationEntity();

    entity.setId(id);
    entity.setStatus(status);
    entity.setApplicationContent(content);
    entity.setSchemaVersion(schemaVersion);
    entity.setCreatedAt(createdAt);
    entity.setModifiedAt(modifiedAt);

    assertThat(entity.getId()).isEqualTo(id);
    assertThat(entity.getStatus()).isEqualTo(status);
    assertThat(entity.getApplicationContent()).isEqualTo(content);
    assertThat(entity.getSchemaVersion()).isEqualTo(schemaVersion);
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(modifiedAt);

    assertThat(entity.getCreatedBy()).isNull();
    assertThat(entity.getUpdatedBy()).isNull();
  }
}
