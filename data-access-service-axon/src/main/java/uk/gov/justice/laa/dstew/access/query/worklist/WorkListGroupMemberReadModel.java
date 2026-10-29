package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Work-list-owned record of an application's linked group, so rows created after the group event
 * can still take the group as their assignment boundary during live processing and replay.
 */
@Entity
@Table(name = "work_list_group_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkListGroupMemberReadModel {
  @Id
  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;
}
