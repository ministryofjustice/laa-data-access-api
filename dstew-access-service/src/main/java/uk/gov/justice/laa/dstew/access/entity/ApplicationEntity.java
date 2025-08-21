package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents an application for legal aid.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "applications")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationEntity implements AuditableEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "UUID")
  private UUID id;

  @Column(name = "provider_firm_id")
  private String providerFirmId;

  @Column(name = "provider_office_id")
  private String providerOfficeId;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "status_code")
  private String statusCode;

  @Column(name = "statement_of_case", length = 1000)
  private String statementOfCase;

  @Column(name = "is_emergency_application")
  private Boolean isEmergencyApplication;

  @OneToMany(mappedBy = "application",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<ApplicationProceedingEntity> proceedings;

  @Embedded
  private EmbeddedRecordHistoryEntity recordHistory = new EmbeddedRecordHistoryEntity();

  @Override
  public Instant getCreatedAt() {
    return recordHistory.getCreatedAt();
  }

  @Override
  public String getCreatedBy() {
    return recordHistory.getCreatedBy();
  }

  @Override
  public Instant getUpdatedAt() {
    return recordHistory.getUpdatedAt();
  }

  @Override
  public String getUpdatedBy() {
    return recordHistory.getUpdatedBy();
  }
}
