package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "status_code_lookup")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StatusCodeLookupEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "code")
  private String code;

  @Column(name = "description")
  private String description;

  @Column(name = "created_at")
  private Instant createdAt;
}