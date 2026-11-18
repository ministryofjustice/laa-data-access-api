package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Authoritative free-text payload shared by the histories of one accepted group action. */
@Entity
@Table(name = "linked_group_justification")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class LinkedGroupJustification {
  @Id private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false)
  private String justification;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
