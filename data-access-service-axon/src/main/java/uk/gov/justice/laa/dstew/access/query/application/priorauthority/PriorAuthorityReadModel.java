package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Replayable current-state read model for a prior-authority submission. */
@Entity
@Table(name = "prior_authority_current_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityReadModel {

  @Id
  @Column(name = "submission_id")
  private UUID submissionId;

  @Column(name = "application_id")
  private UUID applicationId;

  private String status;

  @Column(name = "created_at")
  private Instant createdAt;
}
