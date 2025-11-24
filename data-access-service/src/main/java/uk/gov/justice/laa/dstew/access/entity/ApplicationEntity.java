package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Represents an application.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "application")
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationEntity implements AuditableEntity {

  @Id
  @Column(columnDefinition = "UUID")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplicationStatus status;

  @Column(name = "application_reference")
  private String applicationReference;

  @Type(JsonType.class)
  @Column(columnDefinition = "json")
  private Map<String, Object> applicationContent;

  @ManyToMany(cascade = CascadeType.PERSIST)
  @JoinTable(
      name = "linked_individual",
      joinColumns = @JoinColumn(name = "application_id"),
      inverseJoinColumns = @JoinColumn(name = "individual_id")
  )
  private Set<IndividualEntity> individuals;

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
