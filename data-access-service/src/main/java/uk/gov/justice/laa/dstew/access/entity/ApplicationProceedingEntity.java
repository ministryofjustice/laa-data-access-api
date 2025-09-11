package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents the proceedings that form part of an application for legal aid.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "applications_proceedings")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationProceedingEntity implements AuditableEntity {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "UUID")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "application_id", nullable = false)
  private ApplicationEntity application;

  @Column(name = "proceeding_code")
  private String proceedingCode;

  @Column(name = "level_of_service_code")
  private String levelOfServiceCode;

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
