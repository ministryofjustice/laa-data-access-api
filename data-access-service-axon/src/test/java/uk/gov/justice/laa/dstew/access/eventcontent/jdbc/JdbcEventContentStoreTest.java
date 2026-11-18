package uk.gov.justice.laa.dstew.access.eventcontent.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

class JdbcEventContentStoreTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  @Test
  void givenUnsafeSchemaName_whenCreated_thenRejected() {
    assertThatThrownBy(() -> new JdbcEventContentStore(jdbc, new EventJson(), "axon; drop"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid schema name: axon; drop");
  }

  @Test
  void givenNoTags_whenFound_thenNoQueryIsRun() {
    var store = new JdbcEventContentStore(jdbc, new EventJson(), "axon");

    assertThat(store.findByTags(List.of())).isEmpty();
    verifyNoInteractions(jdbc);
  }
}
