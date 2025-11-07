package uk.gov.justice.laa.dstew.access.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEntityTest {

  @Test
  void testGettersAndSetters() {
    UUID id = UUID.randomUUID();
    UUID statusId = UUID.randomUUID();
    Map<String, Object> content = new HashMap<>();
    content.put("foo", "bar");
    Integer schemaVersion = 1;
    Instant createdAt = Instant.now();
    Instant modifiedAt = Instant.now();

    ApplicationEntity entity = new ApplicationEntity();

    entity.setId(id);
    entity.setStatusId(statusId);
    entity.setApplicationContent(content);
    entity.setSchemaVersion(schemaVersion);
    entity.setCreatedAt(createdAt);
    entity.setModifiedAt(modifiedAt);

    assertThat(entity.getId()).isEqualTo(id);
    assertThat(entity.getStatusEntity().getId()).isEqualTo(statusId);
    assertThat(entity.getApplicationContent()).isEqualTo(content);
    assertThat(entity.getSchemaVersion()).isEqualTo(schemaVersion);
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(modifiedAt);

    assertThat(entity.getCreatedBy()).isNull();
    assertThat(entity.getUpdatedBy()).isNull();
  }
}
