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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a case worker.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "caseworkers")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaseworkerEntity {
  @Id
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;
}
