package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Composite identity for a replayable work-list item. */
@NoArgsConstructor
@EqualsAndHashCode
@Getter
public class WorkListItemId implements Serializable {
  @Enumerated(EnumType.STRING)
  WorkItemType itemType;
  UUID itemId;

  public WorkListItemId(WorkItemType itemType, UUID itemId) {
    this.itemType = itemType;
    this.itemId = itemId;
  }
}

