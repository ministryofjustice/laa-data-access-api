package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a case worker.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "caseworkers")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaseworkerEntity {
  @Id
  @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "created_at")
  @CreationTimestamp
  @EqualsAndHashCode.Exclude
  private Instant createdAt;

  @Column(name = "modified_at")
  @UpdateTimestamp
  @EqualsAndHashCode.Exclude
  private Instant modifiedAt;
}
