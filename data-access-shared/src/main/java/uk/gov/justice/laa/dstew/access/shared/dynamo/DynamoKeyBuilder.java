package uk.gov.justice.laa.dstew.access.shared.dynamo;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.Key;

/**
 * Helper class for building DynamoDB partition key (pk) and sort key (sk) values
 * following the single-table design pattern.
 *
 * <p>PK format: {@code <type>#<uuid>} e.g. {@code application#123e4567-e89b-12d3-a456-426614174000}
 * <p>SK format: {@code <timestamp>} e.g. {@code 2026-01-15T12:34:56.789Z}
 *
 * <p>For GSI keys (gs1pk, gs1sk), use the provided builder methods with appropriate prefixes.
 *
 * <p>Use {@link #key(String, UUID, Instant)} or similar methods to get a {@link Key} object
 * for use with DynamoDB Enhanced Client requests.
 */
public final class DynamoKeyBuilder {

  private static final String DELIMITER = "#";
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  private DynamoKeyBuilder() {
    // utility class
  }

  // ========== Key (Enhanced Client) ==========

  /**
   * Build a Key object for use with DynamoDB Enhanced Client.
   *
   * @param type      the entity type (e.g., "application")
   * @param id        the unique identifier
   * @param timestamp the sort key timestamp
   * @return Key object for enhanced client requests
   */
  public static Key key(String type, UUID id, Instant timestamp) {
    return Key.builder()
        .partitionValue(pk(type, id))
        .sortValue(sk(timestamp))
        .build();
  }

  /**
   * Build a Key object for use with DynamoDB Enhanced Client.
   *
   * @param type the entity type
   * @param id   the unique identifier as string
   * @param sk   the sort key value
   * @return Key object for enhanced client requests
   */
  public static Key key(String type, String id, String sk) {
    return Key.builder()
        .partitionValue(pk(type, id))
        .sortValue(sk)
        .build();
  }

  /**
   * Build a Key object from pre-built pk and sk strings.
   *
   * @param pk the partition key value
   * @param sk the sort key value
   * @return Key object for enhanced client requests
   */
  public static Key key(String pk, String sk) {
    return Key.builder()
        .partitionValue(pk)
        .sortValue(sk)
        .build();
  }

  /**
   * Build a Key object with partition key only (for tables without sort key).
   *
   * @param type the entity type
   * @param id   the unique identifier
   * @return Key object for enhanced client requests
   */
  public static Key keyPkOnly(String type, UUID id) {
    return Key.builder()
        .partitionValue(pk(type, id))
        .build();
  }

  /**
   * Build a Key object with partition key only from a pre-built pk string.
   *
   * @param pk the partition key value
   * @return Key object for enhanced client requests
   */
  public static Key keyPkOnly(String pk) {
    return Key.builder()
        .partitionValue(pk)
        .build();
  }

  // ========== PK (Partition Key) ==========

  /**
   * Build a partition key from type and UUID.
   *
   * @param type the entity type (e.g., "application", "individual", "proceeding")
   * @param id   the unique identifier
   * @return formatted pk value, e.g. "application#123e4567-e89b-12d3-a456-426614174000"
   */
  public static String pk(String type, UUID id) {
    return type.toLowerCase() + DELIMITER + id.toString();
  }

  /**
   * Build a partition key from type and string ID.
   *
   * @param type the entity type
   * @param id   the unique identifier as string
   * @return formatted pk value
   */
  public static String pk(String type, String id) {
    return type.toLowerCase() + DELIMITER + id;
  }

  // ========== SK (Sort Key) ==========

  /**
   * Build a sort key from a timestamp.
   *
   * @param timestamp the instant to use
   * @return ISO-8601 formatted timestamp string, e.g. "2026-01-15T12:34:56.789Z"
   */
  public static String sk(Instant timestamp) {
    return TIMESTAMP_FORMATTER.format(timestamp);
  }

  /**
   * Build a sort key from current time.
   *
   * @return ISO-8601 formatted timestamp string for now
   */
  public static String skNow() {
    return sk(Instant.now());
  }

  /**
   * Build a sort key with a prefix and timestamp.
   * Useful for different sort key patterns, e.g. "SEQ#0000000001" or "TS#2026-01-15T12:34:56Z".
   *
   * @param prefix    the prefix (e.g., "TS", "SEQ", "EVENT")
   * @param timestamp the instant
   * @return formatted sk value, e.g. "TS#2026-01-15T12:34:56.789Z"
   */
  public static String sk(String prefix, Instant timestamp) {
    return prefix.toUpperCase() + DELIMITER + TIMESTAMP_FORMATTER.format(timestamp);
  }

  /**
   * Build a sort key with a prefix and sequence number (zero-padded to 12 digits).
   *
   * @param prefix   the prefix (e.g., "SEQ")
   * @param sequence the sequence number
   * @return formatted sk value, e.g. "SEQ#000000000042"
   */
  public static String sk(String prefix, long sequence) {
    return prefix.toUpperCase() + DELIMITER + String.format("%012d", sequence);
  }

  // ========== GS1PK / GS1SK (GSI keys) ==========

  /**
   * Build a GSI partition key with a prefix and value.
   *
   * @param prefix the prefix (e.g., "PUBLISH", "AGGTYPE", "EVTYPE", "CORR")
   * @param value  the value (e.g., "PENDING", "Application", "APPLICATION_UPDATED", correlationId)
   * @return formatted gs1pk value, e.g. "PUBLISH#PENDING"
   */
  public static String gs1pk(String prefix, String value) {
    return prefix.toUpperCase() + DELIMITER + value;
  }

  /**
   * Build a GSI sort key with a prefix and timestamp.
   *
   * @param prefix    the prefix (e.g., "CREATED", "OCCURRED")
   * @param timestamp the instant
   * @return formatted gs1sk value, e.g. "CREATED#2026-01-15T12:34:56.789Z"
   */
  public static String gs1sk(String prefix, Instant timestamp) {
    return prefix.toUpperCase() + DELIMITER + TIMESTAMP_FORMATTER.format(timestamp);
  }

  /**
   * Build a GSI sort key with a prefix and string value.
   *
   * @param prefix the prefix
   * @param value  the value
   * @return formatted gs1sk value
   */
  public static String gs1sk(String prefix, String value) {
    return prefix.toUpperCase() + DELIMITER + value;
  }

  /**
   * Build a Key object for GSI queries.
   *
   * @param gs1pk the GSI partition key value
   * @param gs1sk the GSI sort key value
   * @return Key object for GSI queries
   */
  public static Key gs1Key(String gs1pk, String gs1sk) {
    return Key.builder()
        .partitionValue(gs1pk)
        .sortValue(gs1sk)
        .build();
  }

  /**
   * Build a Key object for GSI queries with partition key only.
   *
   * @param gs1pk the GSI partition key value
   * @return Key object for GSI queries
   */
  public static Key gs1KeyPkOnly(String gs1pk) {
    return Key.builder()
        .partitionValue(gs1pk)
        .build();
  }

  // ========== Parsing ==========

  /**
   * Extract the type from a partition key.
   *
   * @param pk the partition key value
   * @return the type portion before the delimiter
   */
  public static String extractType(String pk) {
    int idx = pk.indexOf(DELIMITER);
    return idx > 0 ? pk.substring(0, idx) : pk;
  }

  /**
   * Extract the ID from a partition key.
   *
   * @param pk the partition key value
   * @return the ID portion after the delimiter
   */
  public static String extractId(String pk) {
    int idx = pk.indexOf(DELIMITER);
    return idx > 0 ? pk.substring(idx + 1) : pk;
  }

  /**
   * Extract the ID as UUID from a partition key.
   *
   * @param pk the partition key value
   * @return the ID as UUID
   */
  public static UUID extractUuid(String pk) {
    return UUID.fromString(extractId(pk));
  }

  /**
   * Parse a timestamp sort key back to Instant.
   *
   * @param sk the sort key value (ISO-8601 timestamp)
   * @return the parsed Instant
   */
  public static Instant parseTimestamp(String sk) {
    // Handle prefixed timestamps like "TS#2026-01-15T12:34:56.789Z"
    if (sk.contains(DELIMITER)) {
      sk = sk.substring(sk.indexOf(DELIMITER) + 1);
    }
    return Instant.parse(sk);
  }
}

