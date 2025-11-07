package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents an application.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "application")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationEntity implements AuditableEntity {

  @Id
  @Column(columnDefinition = "UUID")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_id", referencedColumnName = "id")
  private StatusCodeLookupEntity statusEntity;

  @Type(JsonType.class)
  @Column(columnDefinition = "json")
  private Map<String, Object> applicationContent;
  @Column(name = "schema_version")
  private Integer schemaVersion;
  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;
  @Column(name = "modified_at")
  @UpdateTimestamp
  private Instant modifiedAt;

  // getters and setters
  public Map<String, Object> getApplicationContent() {
    return applicationContent;
  }

  public void setApplicationContent(Map<String, Object> applicationContent) {
    this.applicationContent = applicationContent;
  }

  /**
   * Sets the status id.
   *
   * @param statusId the identifier for the status.
   */
  public void setStatusId(UUID statusId) {
    if (statusEntity == null) {
      statusEntity = new StatusCodeLookupEntity();
    }
    statusEntity.setId(statusId);
  }

  @Override
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getCreatedBy() {
    return null;
  }

  @Override
  public Instant getUpdatedAt() {
    return modifiedAt;
  }

  @Override
  public String getUpdatedBy() {
    return null;
  }
}
