package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Represents a certificate.
 */
@ExcludeFromGeneratedCodeCoverage
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "certificates")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CertificateEntity implements AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Type(JsonType.class)
  @Column(name = "certificate_content", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> certificateContent;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  @Column(name = "modified_at", nullable = false)
  @UpdateTimestamp
  private Instant modifiedAt;

  @Column(name = "updated_by", nullable = false)
  private String updatedBy;

  @Override
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getCreatedBy() {
    return createdBy;
  }

  @Override
  public Instant getUpdatedAt() {
    return modifiedAt;
  }

  @Override
  public String getUpdatedBy() {
    return updatedBy;
  }
}
