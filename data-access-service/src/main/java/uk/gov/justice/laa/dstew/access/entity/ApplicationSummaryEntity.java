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
@Table(name = "application")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationSummaryEntity {
  @Id
  @Column(name = "application_id")
  private UUID id;

  @Column(name = "application_status")
  private String applicationStatus;

  @Column(name = "application_type")
  private String applicationType;

  @Column(name = "category_of_law")
  private String lawCategory;

  @Column(name = "matter_types")
  private String matterTypes;

  @Column(name = "delegated_functions_used")
  private Boolean delegatedFunctionsUsed;

  @Column(name = "assigned_to")
  private String assignedTo;

  @Column(name = "overall_means_decision")
  private String overallMeansDecision;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "last_updated_at")
  private Instant lastUpdatedAt;

  /*
    {
      "application_id": "LAA1",
      "application_status": "open",
      "application_type": "initial",
      "submitted_at": "2025-09-16T13:43:36.899Z",
      "last_updated_at": "2025-09-16T13:43:36.899Z",
      "category_of_law": "family",
      "matter_types": "special children's act",
      "delegated_functions_used": true,
      "assigned_to": null,
      "overall_means_decision": "passported"
    },
   */
}
