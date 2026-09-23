package uk.gov.justice.laa.dstew.dataaccesstools.cli.local;

import java.util.List;
import java.util.UUID;
import picocli.CommandLine;

abstract class LocalEventStoreCommand {
  @CommandLine.Option(
      names = "--jdbc-url",
      required = true,
      description = "JDBC URL for the local PostgreSQL database.")
  private String jdbcUrl;

  @CommandLine.Option(
      names = "--db-username",
      defaultValue = "postgres",
      description = "Database username.")
  private String username;

  @CommandLine.Option(
      names = "--db-password",
      defaultValue = "postgres",
      description = "Database password.")
  private String password;

  @CommandLine.Option(
      names = "--axon-schema",
      defaultValue = "axon",
      description = "Axon database schema.")
  private String schema;

  @CommandLine.Option(names = "--json", description = "Print one JSON object per event.")
  private boolean json;

  final int printEvents(String aggregateType, UUID aggregateId) {
    List<LocalEventStoreReader.StoredEvent> events =
        new LocalEventStoreReader(jdbcUrl, username, password, schema).read(aggregateId);
    if (events.isEmpty()) {
      System.err.printf("No event stream found for %s %s.%n", aggregateType, aggregateId);
      return 1;
    }
    events.forEach(event -> print(event));
    return 0;
  }

  private void print(LocalEventStoreReader.StoredEvent event) {
    if (json) {
      System.out.printf(
          "{\"aggregateId\":\"%s\",\"eventIdentifier\":\"%s\",\"sequenceNumber\":%d,\"timestamp\":%s,\"payloadType\":%s,\"payload\":%s,\"metadata\":%s}%n",
          event.aggregateId(),
          event.eventIdentifier(),
          event.sequenceNumber(),
          json(event.timestamp()),
          json(event.payloadType()),
          json(event.payload()),
          json(event.metadata()));
      return;
    }
    System.out.printf(
        "%d  %s  %s  %s%n",
        event.sequenceNumber(), event.timestamp(), event.payloadType(), event.eventIdentifier());
    System.out.printf("  payload: %s%n", event.payload());
    if (event.metadata() != null) {
      System.out.printf("  metadata: %s%n", event.metadata());
    }
  }

  private static String json(String value) {
    if (value == null) {
      return "null";
    }
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        + "\"";
  }
}
