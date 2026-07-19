package uk.gov.justice.laa.dstew.access.command.application.data;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only sensitive data associated with one version of an Application aggregate. */
@Entity
@Table(name = "application_data")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationData {

  @EmbeddedId private ApplicationDataId id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private ApplicationDataPayload payload;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
