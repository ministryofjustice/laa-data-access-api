package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


/**
 * Entity representing an application.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "application")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationEntity implements AuditableEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "provider_firm_id", nullable = false)
  private String providerFirmId;

  @Column(name = "provider_office_id", nullable = false)
  private String providerOfficeId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "status_id")
  private UUID statusId; // foreign key to status_code_lookup

  @Column(name = "status_code")
  private String statusCode;

  @Column(name = "application_reference")
  private String applicationReference;

  @Column(name = "statement_of_case", length = 1000)
  private String statementOfCase;

  @Column(name = "is_emergency_application")
  private Boolean isEmergencyApplication;

  @Column(name = "schema_version")
  private Integer schemaVersion;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Column(name = "modified_at")
  private Instant updatedAt;

  @Column(name = "modified_by")
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
    return updatedAt;
  }

  @Override
  public String getUpdatedBy() {
    return updatedBy;
  }
}
