package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Replayable current-state read model for a linked application group. */
@Entity
@Table(name = "linked_application_group_current_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedApplicationGroupReadModel {

  @Id
  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "lead_application_id")
  private UUID leadApplicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "member_ids")
  private List<UUID> memberIds;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "modified_at")
  private Instant modifiedAt;
}
