package uk.gov.justice.laa.dstew.access.query.genesis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** A pointer from a genesis application to one event that relates to it. */
@Entity
@Table(
    name = "genesis_stream",
    uniqueConstraints =
        @UniqueConstraint(
            name = "genesis_stream_event_uk",
            columnNames = {"genesis_id", "source", "event_identifier"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class GenesisStreamEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "genesis_id", nullable = false)
  private UUID genesisId;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "event_identifier", nullable = false)
  private String eventIdentifier;

  @Column(name = "event_name", nullable = false)
  private String eventName;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
