package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents an application summary for legal aid.
 * Will be removed when merged into new application structures
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "application")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationSummaryEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_id", referencedColumnName = "id")
  private StatusCodeLookupEntity statusCodeLookupEntity;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;

}
