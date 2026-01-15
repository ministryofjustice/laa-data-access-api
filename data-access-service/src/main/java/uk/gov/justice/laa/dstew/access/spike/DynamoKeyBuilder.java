package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import java.time.format.DateTimeFormatter;


public final class DynamoKeyBuilder {

  private static final String DELIMITER = "#";
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  private DynamoKeyBuilder() {
    // utility class
  }

  public static String pk(String type, String id) {
    return type.toLowerCase() + DELIMITER + id;
  }



  public static String sk(EventType eventType, Instant timestamp) {
    return eventType.name() + TIMESTAMP_FORMATTER.format(timestamp);
  }

}

