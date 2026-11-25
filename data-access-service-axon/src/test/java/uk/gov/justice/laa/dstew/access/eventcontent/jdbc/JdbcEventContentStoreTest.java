package uk.gov.justice.laa.dstew.access.eventcontent.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

@ExtendWith(MockitoExtension.class)
class JdbcEventContentStoreTest {

  @Mock private JdbcTemplate jdbc;
  @Mock private Connection connection;
  @Mock private PreparedStatement statement;
  @Mock private Array eventIdArray;
  @Mock private ResultSet resultSet;

  @Test
  void givenUnsafeSchemaName_whenCreated_thenRejected() {
    assertThatThrownBy(() -> new JdbcEventContentStore(jdbc, new EventJson(), "axon; drop"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid schema name: axon; drop");
  }

  @Test
  void givenNoEventIds_whenFound_thenNoQueryIsRun() {
    var store = new JdbcEventContentStore(jdbc, new EventJson(), "axon");

    assertThat(store.findByEventIds(List.of())).isEmpty();
    verifyNoInteractions(jdbc);
  }

  @Test
  void givenEventIds_whenFound_thenContentIsReadByEventId() throws SQLException {
    var store = new JdbcEventContentStore(jdbc, new EventJson(), "axon");
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(connection.createArrayOf("varchar", new Object[] {"event-1", "event-2"}))
        .thenReturn(eventIdArray);
    when(resultSet.getString("event_id")).thenReturn("event-1");
    when(resultSet.getString("content")).thenReturn("{\"note\":\"secret\"}");
    doAnswer(
            invocation -> {
              invocation.<RowCallbackHandler>getArgument(1).processRow(resultSet);
              return null;
            })
        .when(jdbc)
        .query(any(PreparedStatementCreator.class), any(RowCallbackHandler.class));

    var content = store.findByEventIds(List.of("event-1", "event-2"));

    assertThat(content).containsOnlyKeys("event-1");
    assertThat(content.get("event-1").get("note").asString()).isEqualTo("secret");
    var creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
    verify(jdbc).query(creator.capture(), any(RowCallbackHandler.class));
    assertThat(creator.getValue().createPreparedStatement(connection)).isSameAs(statement);
    verify(connection)
        .prepareStatement(
            "SELECT event_id, content::text AS content FROM axon.event_content"
                + " WHERE event_id = ANY (?)");
    verify(statement).setArray(1, eventIdArray);
  }

  @Test
  void givenNoEventIds_whenErased_thenNoUpdateIsRun() {
    var store = new JdbcEventContentStore(jdbc, new EventJson(), "axon");

    store.erase(List.of());

    verifyNoInteractions(jdbc);
  }

  @Test
  void givenEventIds_whenErased_thenContentIsReplacedWithAnEmptyObject() throws SQLException {
    var store = new JdbcEventContentStore(jdbc, new EventJson(), "axon");
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(connection.createArrayOf("varchar", new Object[] {"event-1", "event-2"}))
        .thenReturn(eventIdArray);

    store.erase(List.of("event-1", "event-2"));

    var creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
    verify(jdbc).update(creator.capture());
    assertThat(creator.getValue().createPreparedStatement(connection)).isSameAs(statement);
    verify(connection)
        .prepareStatement(
            "UPDATE axon.event_content SET content = '{}'::jsonb, erased_at = now()"
                + " WHERE event_id = ANY (?) AND erased_at IS NULL");
    verify(statement).setArray(1, eventIdArray);
  }
}
